package dev.mtpl.freezemute;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Settings, read from {@code config/freezemute/config.json}.
 *
 * <p>The file is written with the defaults the first time the mod runs, so everything can be
 * changed without rebuilding. Unknown or missing keys fall back to the default.
 */
public final class FreezeMuteConfig {
	private static volatile FreezeMuteConfig instance = new FreezeMuteConfig();

	/** Frozen players cannot break or place blocks, hit anything, or move items around. */
	public boolean freezeBlocksInteractions = true;
	/** Frozen players cannot be hurt, so a mob or a fall cannot kill them while you deal with them. */
	public boolean freezeProtectsFromDamage = true;
	/** Muted players cannot write signs or books either, which is the usual way around a mute. */
	public boolean muteBlocksSignsAndBooks = true;
	/** Tell online staff when a muted player tries to talk or a frozen player tries to run. */
	public boolean notifyStaff = true;
	/** How long to wait before telling staff about the same player again. */
	public int staffNotifyCooldownSeconds = 10;
	/** Check GitHub for a newer release on every server start and install it for the next one. */
	public boolean autoUpdate = true;
	/** Report that an update exists without installing it. */
	public boolean updateCheckOnly = false;
	/** Which repository the update comes from. Nothing outside this repository is ever fetched. */
	public String updateRepository = "poezeloezewoefke1/make-the-players-listen";

	// ------------------------------------------------------------------- lobby

	/** Write the {@code astra:lobby} dimension datapack into the world folder on every start. */
	public boolean lobbyInstallDimension = true;
	/** Lay a stone platform under the lobby spawn the first time the dimension is empty. */
	public boolean lobbySpawnPlatform = true;
	/** How wide that platform is, measured from the middle. */
	public int lobbyPlatformRadius = 12;
	/** Hide lobby members from each other. Staff always see everybody. */
	public boolean lobbyIsolateMembers = true;
	/** How long a queued player keeps their place in line after dropping out. */
	public int lobbyQueueGraceSeconds = 300;
	/** How long an admitted player keeps their slot after dropping out. */
	public int lobbySlotGraceSeconds = 300;
	/** How many players may be let through per second, so a rush does not stall the server. */
	public int lobbyAdmitPerSecond = 1;
	/** Anything below this height in the lobby is caught and put back on the last checkpoint. */
	public int lobbyVoidCatchY = -5;
	/** Parkour checkpoints trigger within this many blocks. */
	public double lobbyCheckpointRadius = 1.5D;
	/** How close you have to stand to the queue point for a right click to count. */
	public double lobbyQueuePointRadius = 4.0D;

	// ------------------------------------------------- the figure on the pedestal

	/** What floats over the figure's head. Drawn bold and gold. */
	public String lobbyNpcText = "Join record!";
	/**
	 * What it wears on its head - any item id.
	 *
	 * <p>A dragon head by default: it is dramatic, it is the right size, and it exists in every
	 * version, which a downloaded skin does not. Set it to {@code player_head} if you would rather
	 * point it at a real one afterwards, or to {@code none} for a bare stand.
	 */
	public String lobbyNpcHead = "dragon_head";
	/** The armour material it wears - {@code diamond}, {@code netherite}, {@code gold}, and so on. */
	public String lobbyNpcArmour = "diamond";
	/** What it holds. */
	public String lobbyNpcHeld = "lantern";

	public static FreezeMuteConfig get() {
		return instance;
	}

	public long staffNotifyCooldownMillis() {
		return Math.max(0, staffNotifyCooldownSeconds) * 1000L;
	}

	public long lobbyQueueGraceMillis() {
		return Math.max(0, lobbyQueueGraceSeconds) * 1000L;
	}

	public long lobbySlotGraceMillis() {
		return Math.max(0, lobbySlotGraceSeconds) * 1000L;
	}

	/**
	 * Reads the settings, and writes the file back so that settings added by a later version of the
	 * mod appear in it with their defaults.
	 *
	 * <p>It writes back only when the file was read successfully, or was not there at all. A file
	 * that could not be parsed is left exactly as it is: one missing comma would otherwise cost
	 * somebody every setting they had, and take away the text they needed to see to find the typo.
	 */
	public static void load(Path file) {
		FreezeMuteConfig config = new FreezeMuteConfig();
		boolean readIt = true;

		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(reader);

				if (root.isJsonObject()) {
					JsonObject object = root.getAsJsonObject();
					config.freezeBlocksInteractions = bool(object, "freezeBlocksInteractions", config.freezeBlocksInteractions);
					config.freezeProtectsFromDamage = bool(object, "freezeProtectsFromDamage", config.freezeProtectsFromDamage);
					config.muteBlocksSignsAndBooks = bool(object, "muteBlocksSignsAndBooks", config.muteBlocksSignsAndBooks);
					config.notifyStaff = bool(object, "notifyStaff", config.notifyStaff);
					config.staffNotifyCooldownSeconds = integer(object, "staffNotifyCooldownSeconds", config.staffNotifyCooldownSeconds);
					config.autoUpdate = bool(object, "autoUpdate", config.autoUpdate);
					config.updateCheckOnly = bool(object, "updateCheckOnly", config.updateCheckOnly);
					config.updateRepository = string(object, "updateRepository", config.updateRepository);
					config.lobbyInstallDimension = bool(object, "lobbyInstallDimension", config.lobbyInstallDimension);
					config.lobbySpawnPlatform = bool(object, "lobbySpawnPlatform", config.lobbySpawnPlatform);
					config.lobbyPlatformRadius = integer(object, "lobbyPlatformRadius", config.lobbyPlatformRadius);
					config.lobbyIsolateMembers = bool(object, "lobbyIsolateMembers", config.lobbyIsolateMembers);
					config.lobbyQueueGraceSeconds = integer(object, "lobbyQueueGraceSeconds", config.lobbyQueueGraceSeconds);
					config.lobbySlotGraceSeconds = integer(object, "lobbySlotGraceSeconds", config.lobbySlotGraceSeconds);
					config.lobbyAdmitPerSecond = integer(object, "lobbyAdmitPerSecond", config.lobbyAdmitPerSecond);
					config.lobbyVoidCatchY = integer(object, "lobbyVoidCatchY", config.lobbyVoidCatchY);
					config.lobbyCheckpointRadius = number(object, "lobbyCheckpointRadius", config.lobbyCheckpointRadius);
					config.lobbyQueuePointRadius = number(object, "lobbyQueuePointRadius", config.lobbyQueuePointRadius);
					config.lobbyNpcText = string(object, "lobbyNpcText", config.lobbyNpcText);
					config.lobbyNpcHead = string(object, "lobbyNpcHead", config.lobbyNpcHead);
					config.lobbyNpcArmour = string(object, "lobbyNpcArmour", config.lobbyNpcArmour);
					config.lobbyNpcHeld = string(object, "lobbyNpcHeld", config.lobbyNpcHeld);
				} else {
					readIt = false;
					FreezeMute.LOGGER.warn("{} is not a JSON object. Using the default settings for this run "
							+ "and leaving the file alone so you can fix it.", file);
				}
			} catch (Exception exception) {
				readIt = false;
				config = new FreezeMuteConfig();
				FreezeMute.LOGGER.error("Could not read {}. Using the default settings for this run and leaving "
						+ "the file alone so you can fix it - overwriting it would take your settings with it.",
						file, exception);
			}
		}

		instance = config;

		if (readIt) {
			write(file, config);
		}
	}

	private static void write(Path file, FreezeMuteConfig config) {
		JsonObject object = new JsonObject();
		object.addProperty("freezeBlocksInteractions", config.freezeBlocksInteractions);
		object.addProperty("freezeProtectsFromDamage", config.freezeProtectsFromDamage);
		object.addProperty("muteBlocksSignsAndBooks", config.muteBlocksSignsAndBooks);
		object.addProperty("notifyStaff", config.notifyStaff);
		object.addProperty("staffNotifyCooldownSeconds", config.staffNotifyCooldownSeconds);
		object.addProperty("autoUpdate", config.autoUpdate);
		object.addProperty("updateCheckOnly", config.updateCheckOnly);
		object.addProperty("updateRepository", config.updateRepository);
		object.addProperty("lobbyInstallDimension", config.lobbyInstallDimension);
		object.addProperty("lobbySpawnPlatform", config.lobbySpawnPlatform);
		object.addProperty("lobbyPlatformRadius", config.lobbyPlatformRadius);
		object.addProperty("lobbyIsolateMembers", config.lobbyIsolateMembers);
		object.addProperty("lobbyQueueGraceSeconds", config.lobbyQueueGraceSeconds);
		object.addProperty("lobbySlotGraceSeconds", config.lobbySlotGraceSeconds);
		object.addProperty("lobbyAdmitPerSecond", config.lobbyAdmitPerSecond);
		object.addProperty("lobbyVoidCatchY", config.lobbyVoidCatchY);
		object.addProperty("lobbyCheckpointRadius", config.lobbyCheckpointRadius);
		object.addProperty("lobbyQueuePointRadius", config.lobbyQueuePointRadius);
		object.addProperty("lobbyNpcText", config.lobbyNpcText);
		object.addProperty("lobbyNpcHead", config.lobbyNpcHead);
		object.addProperty("lobbyNpcArmour", config.lobbyNpcArmour);
		object.addProperty("lobbyNpcHeld", config.lobbyNpcHeld);

		try {
			Path parent = file.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Gson gson = new GsonBuilder().setPrettyPrinting().create();

			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				gson.toJson(object, writer);
			}
		} catch (Exception exception) {
			FreezeMute.LOGGER.error("Could not write {}", file, exception);
		}
	}

	private static String string(JsonObject object, String key, String fallback) {
		try {
			return object.has(key) ? object.get(key).getAsString() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static boolean bool(JsonObject object, String key, boolean fallback) {
		try {
			return object.has(key) ? object.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static int integer(JsonObject object, String key, int fallback) {
		try {
			return object.has(key) ? object.get(key).getAsInt() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double number(JsonObject object, String key, double fallback) {
		try {
			return object.has(key) ? object.get(key).getAsDouble() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}
}
