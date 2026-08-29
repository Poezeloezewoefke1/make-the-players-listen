package dev.mtpl.freezemute.lobby;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.mtpl.freezemute.FreezeMute;

/**
 * Everything the lobby remembers, written to {@code config/freezemute/lobby.json}.
 *
 * <p>The queue is in here rather than in memory on purpose. Grace windows exist so a crash does not
 * cost anybody their place, and the worst crash is the one that takes the whole server down - so
 * the line has to survive a restart, not just a disconnect.
 *
 * <p>A player who drops out is not removed; their entry is marked with the moment they went
 * offline and swept once the grace window runs out. That is the whole grace mechanism: one
 * timestamp, two deadlines.
 */
public final class LobbyState {
	private static final LobbyState INSTANCE = new LobbyState();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT_VERSION = 1;
	/** How many finish times to keep per course. */
	private static final int LEADERBOARD_SIZE = 25;

	/** Somebody waiting in line. {@code offlineSince} is 0 while they are connected. */
	public record Waiting(UUID uuid, String name, long joinedAt, long offlineSince) {
		public boolean online() {
			return offlineSince <= 0L;
		}

		/** Whether they have been gone long enough to lose their place. */
		public boolean graceRanOut(long now, long graceMillis) {
			return !online() && now - offlineSince >= graceMillis;
		}

		public Waiting online(String currentName) {
			return new Waiting(uuid, currentName, joinedAt, 0L);
		}

		public Waiting offline(long now) {
			return online() ? new Waiting(uuid, name, joinedAt, now) : this;
		}
	}

	/** Somebody who got through and is holding a slot. */
	public record Admitted(UUID uuid, String name, long since, long offlineSince) {
		public boolean online() {
			return offlineSince <= 0L;
		}

		/** Whether they have been gone long enough to lose their slot. */
		public boolean graceRanOut(long now, long graceMillis) {
			return !online() && now - offlineSince >= graceMillis;
		}

		public Admitted online(String currentName) {
			return new Admitted(uuid, currentName, since, 0L);
		}

		public Admitted offline(long now) {
			return online() ? new Admitted(uuid, name, since, now) : this;
		}
	}

	/**
	 * Where somebody stood before the lobby took them, so being let in puts them back.
	 *
	 * <p>The game mode travels with it. Members are held in adventure mode, and putting a builder
	 * back into survival because the lobby forgot they were in creative would be its own bug.
	 */
	public record Return(String dimension, Spot spot, String gameMode) {
	}

	private final List<Waiting> queue = new CopyOnWriteArrayList<>();
	private final Map<UUID, Admitted> admitted = new ConcurrentHashMap<>();
	private final Map<UUID, String> earlyAccess = new ConcurrentHashMap<>();
	private final Map<UUID, Return> returns = new ConcurrentHashMap<>();
	private final Map<String, Course> courses = new ConcurrentHashMap<>();
	private final Map<String, List<CourseRecord>> records = new ConcurrentHashMap<>();
	private final Object ioLock = new Object();

	private volatile boolean enabled;
	private volatile boolean queueOpen = true;
	private volatile int cap;
	private volatile Spot spawn = LobbyDimension.DEFAULT_SPAWN;
	private volatile Spot queuePoint;
	private volatile Path file;
	private volatile boolean dirty;

	private LobbyState() {
	}

	public static LobbyState get() {
		return INSTANCE;
	}

	// ----------------------------------------------------------------- toggles

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		enabled = value;
		save();
	}

	public boolean queueOpen() {
		return queueOpen;
	}

	public void setQueueOpen(boolean value) {
		queueOpen = value;
		save();
	}

	/** How many players may be in the world at once. Zero means no limit. */
	public int cap() {
		return cap;
	}

	public void setCap(int value) {
		cap = Math.max(0, value);
		save();
	}

	public Spot spawn() {
		return spawn;
	}

	/**
	 * Where players go to ask for a place in line, or null when there is nowhere to ask.
	 *
	 * <p>Its presence decides how the queue is joined. With a point set, arriving in the lobby
	 * puts nobody in the line - they wander, they do the parkour, and they join when they walk up
	 * and right click. Without one, everybody who arrives is queued automatically, which is what
	 * the lobby did before there was anywhere to click.
	 */
	public Spot queuePoint() {
		return queuePoint;
	}

	public boolean joinedAtAPoint() {
		return queuePoint != null;
	}

	public void setQueuePoint(Spot value) {
		queuePoint = value;
		save();
	}

	public void setSpawn(Spot value) {
		spawn = value;
		save();
	}

	// ------------------------------------------------------------ early access

	public boolean hasEarlyAccess(UUID uuid) {
		return earlyAccess.containsKey(uuid);
	}

	public boolean addEarlyAccess(UUID uuid, String name) {
		boolean fresh = earlyAccess.put(uuid, name) == null;
		save();
		return fresh;
	}

	public boolean removeEarlyAccess(UUID uuid) {
		boolean removed = earlyAccess.remove(uuid) != null;

		if (removed) {
			save();
		}

		return removed;
	}

	public Map<UUID, String> earlyAccess() {
		return Map.copyOf(earlyAccess);
	}

	public UUID earlyAccessByName(String name) {
		for (Map.Entry<UUID, String> entry : earlyAccess.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) {
				return entry.getKey();
			}
		}

		return null;
	}

	// ------------------------------------------------------------------- queue

	/** The line, first come first served. */
	public List<Waiting> queue() {
		return List.copyOf(queue);
	}

	public Waiting waiting(UUID uuid) {
		for (Waiting entry : queue) {
			if (entry.uuid().equals(uuid)) {
				return entry;
			}
		}

		return null;
	}

	/** One-based place in line, or 0 when they are not queued. */
	public int position(UUID uuid) {
		int index = 1;

		for (Waiting entry : queue) {
			if (entry.uuid().equals(uuid)) {
				return index;
			}

			index++;
		}

		return 0;
	}

	public int queueSize() {
		return queue.size();
	}

	/** Adds somebody to the back of the line, or refreshes the entry they already had. */
	public Waiting enqueue(UUID uuid, String name, long now) {
		Waiting existing = waiting(uuid);

		if (existing != null) {
			if (existing.online() && existing.name().equals(name)) {
				// Already in line and already connected: nothing to write.
				return existing;
			}

			return replace(existing, existing.online(name));
		}

		Waiting entry = new Waiting(uuid, name, now, 0L);
		queue.add(entry);
		save();
		return entry;
	}

	public Waiting dequeue(UUID uuid) {
		Waiting existing = waiting(uuid);

		if (existing != null && queue.remove(existing)) {
			save();
			return existing;
		}

		return null;
	}

	/** The first person in line who is actually connected. */
	public Waiting nextOnline() {
		for (Waiting entry : queue) {
			if (entry.online()) {
				return entry;
			}
		}

		return null;
	}

	public void markWaitingOffline(UUID uuid, long now) {
		Waiting existing = waiting(uuid);

		if (existing != null && existing.online()) {
			replace(existing, existing.offline(now));
		}
	}

	public int clearQueue() {
		int size = queue.size();
		queue.clear();

		if (size > 0) {
			save();
		}

		return size;
	}

	private Waiting replace(Waiting existing, Waiting updated) {
		int index = queue.indexOf(existing);

		if (index < 0) {
			return existing;
		}

		queue.set(index, updated);
		save();
		return updated;
	}

	// ---------------------------------------------------------------- admitted

	public boolean isAdmitted(UUID uuid) {
		return admitted.containsKey(uuid);
	}

	public Admitted admittedEntry(UUID uuid) {
		return admitted.get(uuid);
	}

	public List<Admitted> admitted() {
		List<Admitted> entries = new ArrayList<>(admitted.values());
		entries.sort(Comparator.comparingLong(Admitted::since));
		return entries;
	}

	/** How many slots are taken. An offline player inside their grace window still holds theirs. */
	public int slotsUsed() {
		return admitted.size();
	}

	public void admit(UUID uuid, String name, long now) {
		Admitted existing = admitted.get(uuid);

		if (existing != null && existing.online() && existing.name().equals(name)) {
			return;
		}

		admitted.put(uuid, existing == null ? new Admitted(uuid, name, now, 0L) : existing.online(name));
		save();
	}

	public Admitted release(UUID uuid) {
		Admitted removed = admitted.remove(uuid);

		if (removed != null) {
			save();
		}

		return removed;
	}

	public void markAdmittedOffline(UUID uuid, long now) {
		Admitted existing = admitted.get(uuid);

		if (existing != null && existing.online()) {
			admitted.put(uuid, existing.offline(now));
			save();
		}
	}

	public int clearAdmitted() {
		int size = admitted.size();
		admitted.clear();

		if (size > 0) {
			save();
		}

		return size;
	}

	public UUID knownByName(String name) {
		for (Waiting entry : queue) {
			if (entry.name().equalsIgnoreCase(name)) {
				return entry.uuid();
			}
		}

		for (Admitted entry : admitted.values()) {
			if (entry.name().equalsIgnoreCase(name)) {
				return entry.uuid();
			}
		}

		return earlyAccessByName(name);
	}

	/** Names the commands offer for tab completion. */
	public List<String> knownNames() {
		List<String> names = new ArrayList<>();

		for (Waiting entry : queue) {
			names.add(entry.name());
		}

		for (Admitted entry : admitted.values()) {
			names.add(entry.name());
		}

		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	// ---------------------------------------------------------------- returning

	/** Remembers where a player was standing in the world before the lobby swallowed them. */
	public void rememberReturn(UUID uuid, String dimension, Spot spot, String gameMode) {
		returns.put(uuid, new Return(dimension, spot, gameMode));
		save();
	}

	public Return takeReturn(UUID uuid) {
		Return spot = returns.remove(uuid);

		if (spot != null) {
			save();
		}

		return spot;
	}

	public boolean hasReturn(UUID uuid) {
		return returns.containsKey(uuid);
	}

	// ----------------------------------------------------------------- parkour

	public Course course(String name) {
		return name == null ? null : courses.get(name.toLowerCase(Locale.ROOT));
	}

	/** Sorted, for anything a person reads. Allocates, so not for the tick loop. */
	public List<Course> courses() {
		List<Course> list = new ArrayList<>(courses.values());
		list.sort(Comparator.comparing(Course::name, String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	/**
	 * The courses in no particular order, without copying or sorting them.
	 *
	 * <p>Every member is checked against every course on every tick, so this path runs twenty
	 * times a second per player waiting in the room. Sorting a list nobody is going to read, that
	 * many times, is worth avoiding.
	 */
	public Collection<Course> courseValues() {
		return courses.values();
	}

	public List<String> courseNames() {
		List<String> names = new ArrayList<>(courses.keySet());
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public void putCourse(Course course) {
		courses.put(course.name().toLowerCase(Locale.ROOT), course);
		save();
	}

	public boolean removeCourse(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		boolean removed = courses.remove(key) != null;
		records.remove(key);

		if (removed) {
			save();
		}

		return removed;
	}

	/**
	 * Files a finished run. Only a player's best time is kept, so the board is a leaderboard and
	 * not a log of every attempt.
	 *
	 * @return true when this run is that player's new personal best
	 */
	public boolean recordTime(String courseName, CourseRecord record) {
		String key = courseName.toLowerCase(Locale.ROOT);
		List<CourseRecord> board = new ArrayList<>(records.getOrDefault(key, List.of()));
		CourseRecord previous = null;

		for (CourseRecord entry : board) {
			if (entry.uuid().equals(record.uuid())) {
				previous = entry;
				break;
			}
		}

		if (previous != null) {
			if (previous.millis() <= record.millis()) {
				return false;
			}

			board.remove(previous);
		}

		board.add(record);
		board.sort(Comparator.comparingLong(CourseRecord::millis));

		if (board.size() > LEADERBOARD_SIZE) {
			board = board.subList(0, LEADERBOARD_SIZE);
		}

		records.put(key, List.copyOf(board));
		save();
		return true;
	}

	public List<CourseRecord> leaderboard(String courseName) {
		return records.getOrDefault(courseName.toLowerCase(Locale.ROOT), List.of());
	}

	public CourseRecord personalBest(String courseName, UUID uuid) {
		for (CourseRecord entry : leaderboard(courseName)) {
			if (entry.uuid().equals(uuid)) {
				return entry;
			}
		}

		return null;
	}

	// ------------------------------------------------------------- persistence

	public void load(Path path) {
		this.file = path;

		synchronized (ioLock) {
			queue.clear();
			admitted.clear();
			earlyAccess.clear();
			returns.clear();
			courses.clear();
			records.clear();
			enabled = false;
			queueOpen = true;
			cap = 0;
			spawn = LobbyDimension.DEFAULT_SPAWN;
			queuePoint = null;
			// Here rather than at the end, because the reads below return early on a missing or
			// unreadable file, and a pending write left over from before the load would then be
			// flushed on top of what was just read.
			dirty = false;

			if (!Files.isRegularFile(path)) {
				return;
			}

			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(reader);

				if (!root.isJsonObject()) {
					FreezeMute.LOGGER.warn("{} is not a JSON object, starting from an empty lobby", path);
					return;
				}

				read(root.getAsJsonObject());
			} catch (Exception exception) {
				FreezeMute.LOGGER.error("Could not read {}, starting from an empty lobby", path, exception);
				queue.clear();
				admitted.clear();
				courses.clear();
				records.clear();
			}
		}

		FreezeMute.LOGGER.info("Lobby: {}, cap {}, {} waiting, {} admitted, {} course(s)",
				enabled ? "on" : "off", cap == 0 ? "none" : String.valueOf(cap),
				queue.size(), admitted.size(), courses.size());
	}

	private void read(JsonObject object) {
		enabled = readBoolean(object, "enabled", false);
		queueOpen = readBoolean(object, "queueOpen", true);
		cap = Math.max(0, readInt(object, "cap", 0));
		spawn = Spot.fromJson(object.getAsJsonObject("spawn"), LobbyDimension.DEFAULT_SPAWN);
		queuePoint = Spot.fromJson(object.getAsJsonObject("queuePoint"), null);

		JsonArray waitingArray = object.getAsJsonArray("queue");

		if (waitingArray != null) {
			for (JsonElement element : waitingArray) {
				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject entry = element.getAsJsonObject();
				UUID uuid = readUuid(entry);

				if (uuid == null || waiting(uuid) != null) {
					continue;
				}

				queue.add(new Waiting(uuid, readString(entry, "name", uuid.toString()),
						readLong(entry, "joinedAt", 0L), readLong(entry, "offlineSince", 0L)));
			}
		}

		JsonArray admittedArray = object.getAsJsonArray("admitted");

		if (admittedArray != null) {
			for (JsonElement element : admittedArray) {
				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject entry = element.getAsJsonObject();
				UUID uuid = readUuid(entry);

				if (uuid == null) {
					continue;
				}

				admitted.put(uuid, new Admitted(uuid, readString(entry, "name", uuid.toString()),
						readLong(entry, "since", 0L), readLong(entry, "offlineSince", 0L)));
			}
		}

		JsonArray earlyArray = object.getAsJsonArray("earlyAccess");

		if (earlyArray != null) {
			for (JsonElement element : earlyArray) {
				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject entry = element.getAsJsonObject();
				UUID uuid = readUuid(entry);

				if (uuid != null) {
					earlyAccess.put(uuid, readString(entry, "name", uuid.toString()));
				}
			}
		}

		JsonArray returnArray = object.getAsJsonArray("returns");

		if (returnArray != null) {
			for (JsonElement element : returnArray) {
				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject entry = element.getAsJsonObject();
				UUID uuid = readUuid(entry);
				Spot spot = Spot.fromJson(entry.getAsJsonObject("spot"), null);

				if (uuid != null && spot != null) {
					returns.put(uuid, new Return(readString(entry, "dimension", "minecraft:overworld"), spot,
							readString(entry, "gameMode", "survival")));
				}
			}
		}

		JsonArray courseArray = object.getAsJsonArray("courses");

		if (courseArray != null) {
			for (JsonElement element : courseArray) {
				if (!element.isJsonObject()) {
					continue;
				}

				Course course = Course.fromJson(element.getAsJsonObject());

				if (course != null) {
					courses.put(course.name().toLowerCase(Locale.ROOT), course);
				}
			}
		}

		JsonObject recordObject = object.getAsJsonObject("records");

		if (recordObject != null) {
			for (Map.Entry<String, JsonElement> entry : recordObject.entrySet()) {
				if (!entry.getValue().isJsonArray()) {
					continue;
				}

				List<CourseRecord> board = new ArrayList<>();

				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					if (!element.isJsonObject()) {
						continue;
					}

					CourseRecord record = CourseRecord.fromJson(element.getAsJsonObject());

					if (record != null) {
						board.add(record);
					}
				}

				board.sort(Comparator.comparingLong(CourseRecord::millis));
				records.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(board));
			}
		}
	}

	/**
	 * Marks the state as needing writing.
	 *
	 * <p>The lobby changes constantly - every arrival, every admission, every second of a grace
	 * window running down - and serialising the whole file for each of those, on the server
	 * thread, is a cost that grows with the number of people waiting. Writes are collapsed and
	 * flushed once a second from the tick loop instead. Worst case a crash loses a second of
	 * queue order, which is what the grace windows exist to make survivable anyway.
	 */
	public void save() {
		dirty = true;
	}

	/** Writes if anything changed since the last write. Called once a second from the ticker. */
	public void flush() {
		if (!dirty) {
			return;
		}

		dirty = false;
		writeNow();
	}

	private void writeNow() {
		Path path = this.file;

		if (path == null) {
			return;
		}

		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);
		root.addProperty("enabled", enabled);
		root.addProperty("queueOpen", queueOpen);
		root.addProperty("cap", cap);
		root.add("spawn", spawn.toJson());

		if (queuePoint != null) {
			root.add("queuePoint", queuePoint.toJson());
		}

		JsonArray waitingArray = new JsonArray();

		for (Waiting entry : queue) {
			JsonObject object = new JsonObject();
			object.addProperty("uuid", entry.uuid().toString());
			object.addProperty("name", entry.name());
			object.addProperty("joinedAt", entry.joinedAt());
			object.addProperty("offlineSince", entry.offlineSince());
			waitingArray.add(object);
		}

		root.add("queue", waitingArray);

		JsonArray admittedArray = new JsonArray();

		for (Admitted entry : admitted()) {
			JsonObject object = new JsonObject();
			object.addProperty("uuid", entry.uuid().toString());
			object.addProperty("name", entry.name());
			object.addProperty("since", entry.since());
			object.addProperty("offlineSince", entry.offlineSince());
			admittedArray.add(object);
		}

		root.add("admitted", admittedArray);

		JsonArray earlyArray = new JsonArray();

		for (Map.Entry<UUID, String> entry : new LinkedHashMap<>(earlyAccess).entrySet()) {
			JsonObject object = new JsonObject();
			object.addProperty("uuid", entry.getKey().toString());
			object.addProperty("name", entry.getValue());
			earlyArray.add(object);
		}

		root.add("earlyAccess", earlyArray);

		JsonArray returnArray = new JsonArray();

		for (Map.Entry<UUID, Return> entry : new LinkedHashMap<>(returns).entrySet()) {
			JsonObject object = new JsonObject();
			object.addProperty("uuid", entry.getKey().toString());
			object.addProperty("dimension", entry.getValue().dimension());
			object.addProperty("gameMode", entry.getValue().gameMode());
			object.add("spot", entry.getValue().spot().toJson());
			returnArray.add(object);
		}

		root.add("returns", returnArray);

		JsonArray courseArray = new JsonArray();

		for (Course course : courses()) {
			courseArray.add(course.toJson());
		}

		root.add("courses", courseArray);

		JsonObject recordObject = new JsonObject();

		for (Map.Entry<String, List<CourseRecord>> entry : records.entrySet()) {
			JsonArray board = new JsonArray();

			for (CourseRecord record : entry.getValue()) {
				board.add(record.toJson());
			}

			recordObject.add(entry.getKey(), board);
		}

		root.add("records", recordObject);

		synchronized (ioLock) {
			try {
				Path parent = path.getParent();

				if (parent != null) {
					Files.createDirectories(parent);
				}

				Path temporary = path.resolveSibling(path.getFileName() + ".tmp");

				try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
					GSON.toJson(root, writer);
				}

				try {
					Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException atomicFailed) {
					Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException exception) {
				FreezeMute.LOGGER.error("Could not write {}", path, exception);
			}
		}
	}

	private static UUID readUuid(JsonObject entry) {
		try {
			return entry.has("uuid") ? UUID.fromString(entry.get("uuid").getAsString()) : null;
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static String readString(JsonObject entry, String key, String fallback) {
		try {
			return entry.has(key) ? entry.get(key).getAsString() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
		try {
			return entry.has(key) ? entry.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static int readInt(JsonObject entry, String key, int fallback) {
		try {
			return entry.has(key) ? entry.get(key).getAsInt() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static long readLong(JsonObject entry, String key, long fallback) {
		try {
			return entry.has(key) ? entry.get(key).getAsLong() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}
}
