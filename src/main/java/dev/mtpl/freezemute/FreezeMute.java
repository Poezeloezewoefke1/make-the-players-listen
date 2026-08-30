package dev.mtpl.freezemute;

import java.nio.file.Path;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import dev.mtpl.freezemute.command.Permissions;
import dev.mtpl.freezemute.kit.KitEnchantments;
import dev.mtpl.freezemute.lobby.LobbyDimension;
import dev.mtpl.freezemute.lobby.LobbyState;
import dev.mtpl.freezemute.lobby.PlayerWorld;
import dev.mtpl.freezemute.update.AutoUpdater;
import dev.mtpl.freezemute.voice.VoiceData;
import dev.mtpl.freezemute.voice.VoiceSupport;

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
		VoiceData.get().load(directory.resolve("voice.json"));
		LobbyState.get().load(directory.resolve("lobby.json"));
		// Has to happen now: the server reads the world's data packs after mod initialisation and
		// before it builds its dimensions, so writing the pack here is what makes astra:lobby exist.
		LobbyDimension.install(FabricLoader.getInstance().getGameDir());
		Permissions.logMode();
		KitEnchantments.logStatus();
		PlayerWorld.logStatus();
		VoiceSupport.logStatus();
		AutoUpdater.start();
		LOGGER.info("Ready - operators can use /freeze, /unfreeze, /mute, /unmute, /kitgive, "
				+ "the /vc commands, /queue and /lobby");
	}

	/**
	 * The running server, remembered from the first server tick and again whenever anybody joins.
	 *
	 * <p>Packet handlers run on netty threads and have to push their work onto the server
	 * thread; this is how they get hold of it without asking the player for its world, which
	 * is the part of the API that gets renamed most often. Anything reached from a tick or from a
	 * connected player can count on it, which is why the disconnect handler can use it to take
	 * somebody off the lobby team.
	 *
	 * @return the server, or null before it has ticked once
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
