package dev.mtpl.freezemute.voice;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.util.Salvage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Who is muted and who is deafened in voice chat.
 *
 * <p>Kept apart from the chat state on purpose: this is read from Simple Voice Chat's own
 * threads for every audio packet, several times a second per player, so the lookups are plain
 * concurrent map reads with no allocation. Every change is written straight to disk, so a
 * restart does not hand somebody their microphone back.
 *
 * <p>Nothing here touches a Simple Voice Chat class. The commands and the state work whether or
 * not the voice chat mod is installed; only {@link VoicePlugin} talks to its API, and that class
 * is loaded by Simple Voice Chat itself.
 */
public final class VoiceData {
	private static final VoiceData INSTANCE = new VoiceData();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT_VERSION = 1;

	private final Map<UUID, VoiceEntry> muted = new ConcurrentHashMap<>();
	private final Map<UUID, VoiceEntry> deafened = new ConcurrentHashMap<>();
	private final Object ioLock = new Object();

	private volatile Path file;

	private VoiceData() {
	}

	public static VoiceData get() {
		return INSTANCE;
	}

	/**
	 * A voice punishment. {@code until} is an epoch millisecond timestamp, or 0 when it lasts
	 * until an operator lifts it.
	 */
	public record VoiceEntry(UUID uuid, String name, String source, long since, long until, String reason) {
		public boolean permanent() {
			return until <= 0L;
		}

		public boolean expired(long now) {
			return !permanent() && now >= until;
		}
	}

	/** Which of the two lists a command is working on. */
	public enum Kind {
		MUTE("muted", "can no longer talk in voice chat", "can talk in voice chat again"),
		DEAFEN("deafened", "can no longer hear voice chat", "can hear voice chat again");

		private final String jsonKey;
		private final String applied;
		private final String lifted;

		Kind(String jsonKey, String applied, String lifted) {
			this.jsonKey = jsonKey;
			this.applied = applied;
			this.lifted = lifted;
		}

		public String jsonKey() {
			return jsonKey;
		}

		/** "muted" / "deafened", for command feedback. */
		public String past() {
			return jsonKey;
		}

		public String appliedText() {
			return applied;
		}

		public String liftedText() {
			return lifted;
		}
	}

	private Map<UUID, VoiceEntry> mapOf(Kind kind) {
		return kind == Kind.MUTE ? muted : deafened;
	}

	// ------------------------------------------------------------------- reads

	/**
	 * The active entry for a player, dropping it once its time is up.
	 *
	 * <p>This is the hot path - it runs for every audio packet - so the common case is a single
	 * map lookup and a comparison.
	 */
	public VoiceEntry entryOf(Kind kind, UUID uuid) {
		VoiceEntry entry = mapOf(kind).get(uuid);

		if (entry == null) {
			return null;
		}

		if (entry.expired(System.currentTimeMillis())) {
			if (mapOf(kind).remove(uuid, entry)) {
				save();
				announceExpiry(kind, entry);
			}

			return null;
		}

		return entry;
	}

	public boolean isMuted(UUID uuid) {
		return entryOf(Kind.MUTE, uuid) != null;
	}

	public boolean isDeafened(UUID uuid) {
		return entryOf(Kind.DEAFEN, uuid) != null;
	}

	/** True when anybody at all is punished, so the plugin can skip its work entirely. */
	public boolean isEmpty() {
		return muted.isEmpty() && deafened.isEmpty();
	}

	/** Everything still running for this kind, expired entries filtered out. */
	public List<VoiceEntry> entries(Kind kind) {
		long now = System.currentTimeMillis();
		List<VoiceEntry> list = new ArrayList<>();

		for (VoiceEntry entry : mapOf(kind).values()) {
			if (!entry.expired(now)) {
				list.add(entry);
			}
		}

		list.sort(Comparator.comparing(VoiceEntry::name, String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	public List<String> names(Kind kind) {
		List<String> names = new ArrayList<>();

		for (VoiceEntry entry : entries(kind)) {
			names.add(entry.name());
		}

		return names;
	}

	public VoiceEntry findByName(Kind kind, String name) {
		long now = System.currentTimeMillis();

		for (VoiceEntry entry : mapOf(kind).values()) {
			if (entry.name().equalsIgnoreCase(name) && !entry.expired(now)) {
				return entry;
			}
		}

		return null;
	}

	// ------------------------------------------------------------------ writes

	/** @return true when this was not already in place. */
	public boolean apply(Kind kind, VoiceEntry entry) {
		boolean fresh = entryOf(kind, entry.uuid()) == null;
		mapOf(kind).put(entry.uuid(), entry);
		save();
		return fresh;
	}

	/** @return the entry that was lifted, or null when there was nothing to lift. */
	public VoiceEntry lift(Kind kind, UUID uuid) {
		VoiceEntry removed = mapOf(kind).remove(uuid);

		if (removed == null) {
			return null;
		}

		save();
		return removed.expired(System.currentTimeMillis()) ? null : removed;
	}

	public int clear(Kind kind) {
		int size = entries(kind).size();
		mapOf(kind).clear();

		if (size > 0) {
			save();
		}

		return size;
	}

	/** Keeps the stored name in sync with the account, so a rename does not break /vcunmute. */
	public void refreshName(UUID uuid, String name) {
		boolean changed = false;

		for (Kind kind : Kind.values()) {
			VoiceEntry entry = mapOf(kind).get(uuid);

			if (entry != null && !entry.name().equals(name)) {
				mapOf(kind).put(uuid, new VoiceEntry(uuid, name, entry.source(), entry.since(), entry.until(),
						entry.reason()));
				changed = true;
			}
		}

		if (changed) {
			save();
		}
	}

	private void announceExpiry(Kind kind, VoiceEntry entry) {
		MinecraftServer server = FreezeMute.server();

		if (server == null) {
			return;
		}

		server.execute(() -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.uuid());

			if (player != null) {
				player.sendMessage(Text.literal("Your voice chat " + kind.past()
						+ " has run out - you " + kind.liftedText() + "."));
			}
		});
	}

	// ------------------------------------------------------------- persistence

	public void load(Path path) {
		this.file = path;

		synchronized (ioLock) {
			muted.clear();
			deafened.clear();

			if (!Files.isRegularFile(path)) {
				return;
			}

			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(reader);

				if (!root.isJsonObject()) {
					FreezeMute.LOGGER.warn("{} is not a JSON object, starting from an empty state", path);
					Salvage.setAside(path);
					return;
				}

				for (Kind kind : Kind.values()) {
					read(root.getAsJsonObject(), kind);
				}
			} catch (Exception exception) {
				FreezeMute.LOGGER.error("Could not read {}, starting from an empty state", path, exception);
				muted.clear();
				deafened.clear();
				Salvage.setAside(path);
			}
		}

		FreezeMute.LOGGER.info("Voice chat: {} muted and {} deafened player(s)", muted.size(), deafened.size());
	}

	private void read(JsonObject root, Kind kind) {
		JsonArray array = root.getAsJsonArray(kind.jsonKey());

		if (array == null) {
			return;
		}

		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject entry = element.getAsJsonObject();
			UUID uuid = readUuid(entry);

			if (uuid == null) {
				continue;
			}

			mapOf(kind).put(uuid, new VoiceEntry(
					uuid,
					readString(entry, "name", uuid.toString()),
					readString(entry, "source", "unknown"),
					readLong(entry, "since", 0L),
					readLong(entry, "until", 0L),
					readString(entry, "reason", "")));
		}
	}

	public void save() {
		Path path = this.file;

		if (path == null) {
			return;
		}

		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);

		for (Kind kind : Kind.values()) {
			JsonArray array = new JsonArray();

			for (VoiceEntry entry : entries(kind)) {
				JsonObject object = new JsonObject();
				object.addProperty("uuid", entry.uuid().toString());
				object.addProperty("name", entry.name());
				object.addProperty("source", entry.source());
				object.addProperty("since", entry.since());
				object.addProperty("until", entry.until());
				object.addProperty("reason", entry.reason());
				array.add(object);
			}

			root.add(kind.jsonKey(), array);
		}

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
		if (!entry.has("uuid")) {
			return null;
		}

		try {
			return UUID.fromString(entry.get("uuid").getAsString());
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

	private static long readLong(JsonObject entry, String key, long fallback) {
		try {
			return entry.has(key) ? entry.get(key).getAsLong() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}
}
