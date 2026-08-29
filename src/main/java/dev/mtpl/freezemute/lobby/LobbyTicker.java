package dev.mtpl.freezemute.lobby;

import java.util.List;
import java.util.UUID;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FreezeMuteConfig;
import dev.mtpl.freezemute.command.Permissions;
import dev.mtpl.freezemute.lobby.LobbyState.Admitted;
import dev.mtpl.freezemute.lobby.LobbyState.Waiting;

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
		LobbyState state = LobbyState.get();

		if (!state.enabled() || server == null) {
			return;
		}

		ticks++;
		boolean display = ticks % DISPLAY_EVERY == 0;
		long now = System.currentTimeMillis();

		// Players whose join has settled enough to be moved, then the upkeep that keeps members
		// hidden from each other.
		LobbyManager.tickPending(server);
		LobbyManager.tickMembers(server);

		if (LobbyManager.memberCount() > 0) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (LobbyManager.isMember(player) && LobbyManager.isInLobby(player)) {
					Parkour.tick(server, player, now, display);
				}
			}
		}

		if (ticks % TICKS_PER_SECOND != 0) {
			return;
		}

		sweepGrace(server, state, now);
		LobbyManager.returnEscapees(server);
		collectStrays(server, state, now);
		admit(server, state);
		updateBars(server, state);
	}

	/**
	 * Drops entries whose grace window has run out.
	 *
	 * <p>There are two windows and they mean different things: a queued player who drops out keeps
	 * their place in line, and an admitted player who drops out keeps their slot. Neither is
	 * touched while the player is connected.
	 */
	private static void sweepGrace(MinecraftServer server, LobbyState state, long now) {
		FreezeMuteConfig config = FreezeMuteConfig.get();
		long queueGrace = config.lobbyQueueGraceMillis();
		long slotGrace = config.lobbySlotGraceMillis();

		for (Waiting entry : state.queue()) {
			if (!entry.online() && now - entry.offlineSince() >= queueGrace) {
				state.dequeue(entry.uuid());
				FreezeMute.LOGGER.info("Lobby: {} lost their place in line after {}s offline",
						entry.name(), queueGrace / 1000L);
			}
		}

		for (Admitted entry : state.admitted()) {
			if (!entry.online() && now - entry.offlineSince() >= slotGrace) {
				state.release(entry.uuid());
				FreezeMute.LOGGER.info("Lobby: {} lost their slot after {}s offline",
						entry.name(), slotGrace / 1000L);
			}
		}
	}

	/**
	 * Puts anybody who is standing in the lobby without a place in line back into it.
	 *
	 * <p>This is what makes the panic button safe: after everyone is pulled back the queue can be
	 * rebuilt without asking anybody to reconnect, and a player who somehow ends up in the lobby
	 * unqueued is not stranded there.
	 */
	private static void collectStrays(MinecraftServer server, LobbyState state, long now) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID uuid = player.getUuid();

			if (!LobbyManager.isInLobby(player)) {
				continue;
			}

			if (Permissions.isStaff(player)) {
				if (LobbyManager.isMember(player)) {
					// Made an operator while standing in the room: they stop being held by it,
					// and stop being hidden from the people who still are.
					LobbyManager.stopBeingMember(server, player);
				}

				continue;
			}

			if (!LobbyManager.isMember(player)) {
				// In the lobby but not held by it: a restart, or a manual teleport.
				LobbyManager.becomeMember(server, player);
			}

			if (!state.isAdmitted(uuid) && state.waiting(uuid) == null) {
				state.enqueue(uuid, player.getGameProfile().name(), now);
			}
		}
	}

	/** Lets people through, oldest first, never more than the throttle allows. */
	private static void admit(MinecraftServer server, LobbyState state) {
		if (!state.queueOpen()) {
			return;
		}

		int cap = state.cap();
		int budget = Math.max(1, FreezeMuteConfig.get().lobbyAdmitPerSecond);

		while (budget > 0) {
			if (cap > 0 && state.slotsUsed() >= cap) {
				return;
			}

			Waiting next = state.nextOnline();

			if (next == null) {
				return;
			}

			ServerPlayerEntity player = server.getPlayerManager().getPlayer(next.uuid());

			if (player == null) {
				// The entry says online but nobody is there; correct it and try the next one.
				state.markWaitingOffline(next.uuid(), System.currentTimeMillis());
				continue;
			}

			LobbyManager.admit(server, player, true);
			budget--;
		}
	}

	/** Refreshes the boss bar of everybody still waiting. */
	private static void updateBars(MinecraftServer server, LobbyState state) {
		List<Waiting> queue = state.queue();
		int total = queue.size();
		boolean open = state.queueOpen();

		for (int index = 0; index < total; index++) {
			Waiting entry = queue.get(index);

			if (!entry.online()) {
				continue;
			}

			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.uuid());

			if (player != null) {
				LobbyManager.updateBar(player, index + 1, total, open);
			}
		}
	}
}
