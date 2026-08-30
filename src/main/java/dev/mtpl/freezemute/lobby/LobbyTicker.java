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
 *
 * <p>Two things happen whether or not the routing is switched on: the state is flushed to disk, and
 * the room itself is ticked. Both are about the lobby existing rather than about the queue running,
 * and the routing is off by default - which is the state staff build the room in.
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

		boolean display = ticks % DISPLAY_EVERY == 0;
		long now = System.currentTimeMillis();

		// The room, before the switch. Everything below this belongs to the queue and is off when
		// the queue is off; the floor under a void world is not - somebody standing in that room
		// can fall out of it whether or not anybody is being routed anywhere, and the routing is
		// off by default, which is exactly the state staff build the place in. This is also what
		// gives them a working timer to try their own jumps with.
		//
		// It costs one world lookup per player per tick, and only while the dimension exists.
		theRoom(server, now, display);

		if (!state.enabled()) {
			return;
		}

		// Players whose join has settled enough to be moved, then the upkeep that keeps members
		// hidden from each other.
		LobbyManager.tickPending(server);
		LobbyManager.tickMembers(server);

		if (!second) {
			return;
		}

		// Everything that happens once a second is decided on the other side of an interface, so
		// it can be driven from a test without a server or anybody standing in the room. See
		// LobbyRules; what is left here is only the clock.
		LobbyRules.tickSecond(new ServerRoom(server), state, FreezeMuteConfig.get(), now);
	}

	/** Parkour timers and the void catch, for everybody standing in the lobby. */
	private static void theRoom(MinecraftServer server, long now, boolean display) {
		if (LobbyDimension.world(server) == null) {
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (LobbyManager.isInLobby(player)) {
				Parkour.tick(server, player, now, display);
			}
		}
	}
}
