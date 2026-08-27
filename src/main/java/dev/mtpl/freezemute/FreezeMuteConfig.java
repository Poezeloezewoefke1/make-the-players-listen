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

	public static FreezeMuteConfig get() {
		return instance;
	}

	public long staffNotifyCooldownMillis() {
		return Math.max(0, staffNotifyCooldownSeconds) * 1000L;
	}

	public static void load(Path file) {
		FreezeMuteConfig config = new FreezeMuteConfig();

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
				} else {
					FreezeMute.LOGGER.warn("{} is not a JSON object, using the default settings", file);
				}
			} catch (Exception exception) {
				FreezeMute.LOGGER.error("Could not read {}, using the default settings", file, exception);
				config = new FreezeMuteConfig();
			}
		}

		instance = config;
		write(file, config);
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
}
