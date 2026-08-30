package dev.mtpl.freezemute;

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

import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.util.Salvage;

import dev.mtpl.freezemute.util.Messages;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Everything the mod remembers: who is frozen and who is muted.
 *
 * <p>The maps are read from the netty threads (packet handlers) and written from the
 * server thread (commands), so they are concurrent. Every mutation is written straight
 * to disk, which means the state survives restarts and crashes.
 */
public final class ModerationData {
	private static final ModerationData INSTANCE = new ModerationData();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FORMAT_VERSION = 1;

	private final Map<UUID, FreezeEntry> frozen = new ConcurrentHashMap<>();
	private final Map<UUID, MuteEntry> muted = new ConcurrentHashMap<>();
	private final Object ioLock = new Object();

	private volatile Path file;

	private ModerationData() {
	}

	public static ModerationData get() {
		return INSTANCE;
	}

	/**
	 * A frozen player. {@code until} is an epoch millisecond timestamp, or 0 for a freeze that
	 * lasts until somebody runs {@code /unfreeze}. {@code wasInvulnerable} remembers whether the
	 * player was already invulnerable before the freeze, so unfreezing puts that back.
	 */
	public record FreezeEntry(UUID uuid, String name, String source, long since, long until, String reason,
			boolean wasInvulnerable) {
		public boolean permanent() {
			return until <= 0L;
		}

		public boolean expired(long now) {
			return !permanent() && now >= until;
		}

		public long remainingMillis(long now) {
			return permanent() ? Long.MAX_VALUE : Math.max(0L, until - now);
		}
	}

	/** A muted player. {@code until} is an epoch millisecond timestamp, or 0 for a permanent mute. */
	public record MuteEntry(UUID uuid, String name, String source, long since, long until, String reason) {
		public boolean permanent() {
			return until <= 0L;
		}

		public boolean expired(long now) {
			return !permanent() && now >= until;
		}

		public long remainingMillis(long now) {
			return permanent() ? Long.MAX_VALUE : Math.max(0L, until - now);
		}
	}

	// ------------------------------------------------------------------ freeze

	public boolean isFrozen(UUID uuid) {
		return freezeOf(uuid) != null;
	}

	/**
	 * Notices punishments that have run out, rather than waiting for something to ask.
	 *
	 * <p>Everything here expires lazily: the entry goes the next time somebody asks whether this
	 * player is frozen or muted, and that is what tells them it is over. For a freeze the asking
	 * is constant, because every movement packet asks. For a mute nothing asks until somebody
	 * speaks - so a mute could run out and the player find out only by trying to talk, which is
	 * the one thing they had been told not to do. Called once a second; two maps that are almost
	 * always empty cost nothing to walk.
	 */
	public void sweepExpired() {
		if (frozen.isEmpty() && muted.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();

		for (FreezeEntry entry : frozen.values()) {
			if (entry.expired(now)) {
				// Through the same door as everything else, so the entry is dropped once and the
				// player is told once however many things notice at the same moment.
				freezeOf(entry.uuid());
			}
		}

		for (MuteEntry entry : muted.values()) {
			if (entry.expired(now)) {
				muteOf(entry.uuid());
			}
		}
	}

	/** The active freeze of a player, dropping it when its time is up. */
	public FreezeEntry freezeOf(UUID uuid) {
		FreezeEntry entry = frozen.get(uuid);

		if (entry == null) {
			return null;
		}

		if (entry.expired(System.currentTimeMillis())) {
			if (frozen.remove(uuid, entry)) {
				save();
				onFreezeExpired(entry);
			}

			return null;
		}

		return entry;
	}

	/** @return true if the player was not already frozen. */
	public boolean freeze(FreezeEntry entry) {
		FreezeEntry previous = frozen.put(entry.uuid(), entry);
		save();
		return previous == null;
	}

	/** @return the removed entry, or null when the player was not frozen. */
	public FreezeEntry unfreeze(UUID uuid) {
		FreezeEntry removed = frozen.remove(uuid);

		if (removed != null) {
			save();
		}

		return removed;
	}

	public int clearFrozen() {
		int size = frozenEntries().size();
		frozen.clear();

		if (size > 0) {
			save();
		}

		return size;
	}

	/** All freezes that are still running, expired ones filtered out. */
	public List<FreezeEntry> frozenEntries() {
		long now = System.currentTimeMillis();
		List<FreezeEntry> entries = new ArrayList<>();

		for (FreezeEntry entry : frozen.values()) {
			if (!entry.expired(now)) {
				entries.add(entry);
			}
		}

		entries.sort(Comparator.comparing(FreezeEntry::name, String.CASE_INSENSITIVE_ORDER));
		return entries;
	}

	public FreezeEntry findFrozenByName(String name) {
		long now = System.currentTimeMillis();

		for (FreezeEntry entry : frozen.values()) {
			if (entry.name().equalsIgnoreCase(name) && !entry.expired(now)) {
				return entry;
			}
		}

		return null;
	}

	// -------------------------------------------------------------------- mute

	/**
	 * Returns the active mute of a player, transparently dropping it when it has expired.
	 */
	public MuteEntry muteOf(UUID uuid) {
		MuteEntry entry = muted.get(uuid);

		if (entry == null) {
			return null;
		}

		if (entry.expired(System.currentTimeMillis())) {
			if (muted.remove(uuid, entry)) {
				save();
				onMuteExpired(entry);
			}

			return null;
		}

		return entry;
	}

	public boolean isMuted(UUID uuid) {
		return muteOf(uuid) != null;
	}

	/** @return true if the player was not already muted. */
	public boolean mute(MuteEntry entry) {
		boolean wasMuted = muteOf(entry.uuid()) != null;
		muted.put(entry.uuid(), entry);
		save();
		return !wasMuted;
	}

	/** @return the removed entry, or null when the player was not muted. */
	public MuteEntry unmute(UUID uuid) {
		MuteEntry removed = muted.remove(uuid);

		if (removed == null) {
			return null;
		}

		save();
		return removed.expired(System.currentTimeMillis()) ? null : removed;
	}

	public int clearMuted() {
		int size = muteEntries().size();
		muted.clear();

		if (size > 0) {
			save();
		}

		return size;
	}

	/** All mutes that are still active, expired ones filtered out. */
	public List<MuteEntry> muteEntries() {
		long now = System.currentTimeMillis();
		List<MuteEntry> entries = new ArrayList<>();

		for (MuteEntry entry : muted.values()) {
			if (!entry.expired(now)) {
				entries.add(entry);
			}
		}

		entries.sort(Comparator.comparing(MuteEntry::name, String.CASE_INSENSITIVE_ORDER));
		return entries;
	}

	public MuteEntry findMutedByName(String name) {
		long now = System.currentTimeMillis();

		for (MuteEntry entry : muted.values()) {
			if (entry.name().equalsIgnoreCase(name) && !entry.expired(now)) {
				return entry;
			}
		}

		return null;
	}

	/** Names of everyone currently frozen or muted, for command tab-completion. */
	public List<String> knownNames(boolean frozenNames, boolean mutedNames) {
		List<String> names = new ArrayList<>();

		if (frozenNames) {
			for (FreezeEntry entry : frozenEntries()) {
				names.add(entry.name());
			}
		}

		if (mutedNames) {
			for (MuteEntry entry : muteEntries()) {
				names.add(entry.name());
			}
		}

		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Keeps the stored name in sync with the account, so name changes do not break /unfreeze. */
	public void refreshName(UUID uuid, String name) {
		boolean changed = false;
		FreezeEntry freeze = frozen.get(uuid);

		if (freeze != null && !freeze.name().equals(name)) {
			frozen.put(uuid, new FreezeEntry(uuid, name, freeze.source(), freeze.since(), freeze.until(),
					freeze.reason(), freeze.wasInvulnerable()));
			changed = true;
		}

		MuteEntry mute = muted.get(uuid);

		if (mute != null && !mute.name().equals(name)) {
			muted.put(uuid, new MuteEntry(uuid, name, mute.source(), mute.since(), mute.until(), mute.reason()));
			changed = true;
		}

		if (changed) {
			save();
		}
	}

	// ------------------------------------------------------------- persistence

	public void load(Path path) {
		this.file = path;

		synchronized (ioLock) {
			frozen.clear();
			muted.clear();

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

				JsonObject object = root.getAsJsonObject();
				readFrozen(object);
				readMuted(object);
			} catch (Exception exception) {
				FreezeMute.LOGGER.error("Could not read {}, starting from an empty state", path, exception);
				frozen.clear();
				muted.clear();
				Salvage.setAside(path);
			}
		}

		FreezeMute.LOGGER.info("Loaded {} frozen and {} muted player(s)", frozen.size(), muteEntries().size());
	}

	private void readFrozen(JsonObject object) {
		JsonArray array = object.getAsJsonArray("frozen");

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

			frozen.put(uuid, new FreezeEntry(
					uuid,
					readString(entry, "name", uuid.toString()),
					readString(entry, "source", "unknown"),
					readLong(entry, "since", 0L),
					readLong(entry, "until", 0L),
					readString(entry, "reason", ""),
					readBoolean(entry, "wasInvulnerable", false)));
		}
	}

	private void readMuted(JsonObject object) {
		JsonArray array = object.getAsJsonArray("muted");

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

			muted.put(uuid, new MuteEntry(
					uuid,
					readString(entry, "name", uuid.toString()),
					readString(entry, "source", "unknown"),
					readLong(entry, "since", 0L),
					readLong(entry, "until", 0L),
					readString(entry, "reason", "")));
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

	private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
		try {
			return entry.has(key) ? entry.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	/** Releases a player whose freeze ran out, and tells them so. */
	private void onFreezeExpired(FreezeEntry entry) {
		MinecraftServer server = FreezeMute.server();

		if (server == null) {
			return;
		}

		server.execute(() -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.uuid());

			if (player != null) {
				FreezeEnforcer.onUnfrozen(player, entry);
				player.sendMessage(Messages.youAreUnfrozen());
			}
		});
	}

	private void onMuteExpired(MuteEntry entry) {
		MinecraftServer server = FreezeMute.server();

		if (server == null) {
			return;
		}

		server.execute(() -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.uuid());

			if (player != null) {
				player.sendMessage(Messages.youAreUnmuted(LobbyManager.isMember(player)));
			}
		});
	}

	private static long readLong(JsonObject entry, String key, long fallback) {
		try {
			return entry.has(key) ? entry.get(key).getAsLong() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	public void save() {
		Path path = this.file;

		if (path == null) {
			return;
		}

		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);

		JsonArray frozenArray = new JsonArray();

		for (FreezeEntry entry : frozenEntries()) {
			JsonObject object = new JsonObject();
			object.addProperty("uuid", entry.uuid().toString());
			object.addProperty("name", entry.name());
			object.addProperty("source", entry.source());
			object.addProperty("since", entry.since());
			object.addProperty("until", entry.until());
			object.addProperty("reason", entry.reason());
			object.addProperty("wasInvulnerable", entry.wasInvulnerable());
			frozenArray.add(object);
		}

		root.add("frozen", frozenArray);

		JsonArray mutedArray = new JsonArray();

		for (MuteEntry entry : muteEntries()) {
			JsonObject object = new JsonObject();
			object.addProperty("uuid", entry.uuid().toString());
			object.addProperty("name", entry.name());
			object.addProperty("source", entry.source());
			object.addProperty("since", entry.since());
			object.addProperty("until", entry.until());
			object.addProperty("reason", entry.reason());
			mutedArray.add(object);
		}

		root.add("muted", mutedArray);

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
}
