package dev.mtpl.freezemute.voice;

import dev.mtpl.freezemute.FreezeMute;

import net.fabricmc.loader.api.FabricLoader;

/**
 * What the rest of the mod is allowed to know about Simple Voice Chat.
 *
 * <p>Deliberately free of any Simple Voice Chat type. {@link VoicePlugin} is the only class that
 * imports the voice chat API, and it reports in here when the voice chat mod loads it - so the
 * commands can say whether the punishments are actually being enforced without ever risking
 * loading a class that is not there.
 */
public final class VoiceSupport {
	/** The mod id Simple Voice Chat uses on both Fabric and Forge. */
	private static final String VOICECHAT_MOD_ID = "voicechat";

	private static volatile boolean active;

	private VoiceSupport() {
	}

	/** True when the voice chat mod is on the server at all. */
	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded(VOICECHAT_MOD_ID);
	}

	/** True once the voice chat mod has loaded this mod's plugin, so mutes are being enforced. */
	public static boolean active() {
		return active;
	}

	/** Called by {@link VoicePlugin}; nothing else should. */
	static void markActive() {
		active = true;
	}

	/** Logged at startup so it is obvious from the log whether the voice commands do anything. */
	public static void logStatus() {
		if (installed()) {
			FreezeMute.LOGGER.info("Simple Voice Chat found - /vcmute and /vcdeafen will be enforced");
		} else {
			FreezeMute.LOGGER.info("Simple Voice Chat not installed - the /vc commands still record punishments, "
					+ "but nothing is enforced until it is added");
		}
	}
}
