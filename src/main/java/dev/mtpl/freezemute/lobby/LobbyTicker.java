package dev.mtpl.freezemute.lobby;

import dev.mtpl.freezemute.FreezeMuteConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The heartbeat: the queue moves, grace windows run out and parkour timers advance from here.
 *
 * <p>Called from the end of every server tick. The per-tick half is deliberately tiny - a parkour
 * position check for the handful of people in the lobby - and everything else happens once a
 * second, because a queue that reacts within a second reacts instantly as far as anybody waiting
 * in it is concerned.
 */
public final class LobbyTicker {
	private static final int TICKS_PER_SECOND = 20;
	/** The parkour clock is redrawn five times a second, which is smooth without being wasteful. */
	private static final int DISPLAY_EVERY = 4;

	private static int ticks;

	private LobbyTicker() {
	}

	public static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}

		LobbyState state = LobbyState.get();
		ticks++;
		boolean second = ticks % TICKS_PER_SECOND == 0;

		if (second) {
			// Before the switch, not after it: building a lobby and setting courses up are done
			// with the routing turned off, and those changes have to reach the disk too.
			state.flush();
		}

		if (!state.enabled()) {
			return;
		}

		boolean display = ticks % DISPLAY_EVERY == 0;
		long now = System.currentTimeMillis();

		// Players whose join has settled enough to be moved, then the upkeep that keeps members
		// hidden from each other.
		LobbyManager.tickPending(server);
		LobbyManager.tickMembers(server);

		// Everybody standing in the room, not only the people the queue is holding. Staff have to
		// be able to run a course to see whether the jumps they just placed are possible, and -
		// more to the point - the void underneath has to catch them too. Until now the only people
		// who could fall out of the lobby and keep falling were the ones who built it.
		if (LobbyDimension.world(server) != null) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (LobbyManager.isInLobby(player)) {
					Parkour.tick(server, player, now, display);
				}
			}
		}

		if (!second) {
			return;
		}

		// Everything that happens once a second is decided on the other side of an interface, so
		// it can be driven from a test without a server or anybody standing in the room. See
		// LobbyRules; what is left here is only the clock.
		LobbyRules.tickSecond(new ServerRoom(server), state, FreezeMuteConfig.get(), now);
	}
}
