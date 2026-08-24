package dev.mtpl.freezemute;

import java.nio.file.Path;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Make The Players Listen - freeze and mute commands for a Fabric server.
 *
 * <p>The mod is completely server side: it never asks the client for anything, so vanilla
 * clients (and any modded client) are affected exactly the same way.
 */
public final class FreezeMute implements ModInitializer {
	public static final String MOD_ID = "freezemute";
	public static final Logger LOGGER = LoggerFactory.getLogger("Make The Players Listen");

	@Override
	public void onInitialize() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("moderation.json");
		ModerationData.get().load(file);
		LOGGER.info("Ready - operators can use /freeze, /unfreeze, /mute and /unmute");
	}
}
