package dev.mtpl.freezemute;

import java.nio.file.Path;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import dev.mtpl.freezemute.command.Permissions;

import net.minecraft.server.MinecraftServer;

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

	private static volatile MinecraftServer server;

	@Override
	public void onInitialize() {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		FreezeMuteConfig.load(directory.resolve("config.json"));
		ModerationData.get().load(directory.resolve("moderation.json"));
		Permissions.logMode();
		LOGGER.info("Ready - operators can use /freeze, /unfreeze, /mute and /unmute");
	}

	/**
	 * The running server, remembered when a player joins.
	 *
	 * <p>Packet handlers run on netty threads and have to push their work onto the server
	 * thread; this is how they get hold of it without asking the player for its world, which
	 * is the part of the API that gets renamed most often.
	 *
	 * @return the server, or null before anybody has joined
	 */
	public static MinecraftServer server() {
		return server;
	}

	public static void rememberServer(MinecraftServer instance) {
		if (instance != null) {
			server = instance;
		}
	}
}
