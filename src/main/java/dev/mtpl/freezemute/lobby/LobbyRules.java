package dev.mtpl.freezemute.lobby;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.mtpl.freezemute.FreezeMuteConfig;
import dev.mtpl.freezemute.lobby.LobbyState.Admitted;
import dev.mtpl.freezemute.lobby.LobbyState.Waiting;

/**
 * What the lobby does once a second, written against {@link Room} rather than against Minecraft.
 *
 * <p>Grace windows running out, people who have wandered out of the room, people standing in it
 * who are not in the line, letting the next ones through, and keeping the boss bars honest. The
 * order matters and is the order below: nobody should be let through into a slot that a sweep is
 * about to free, and no bar should be drawn for a place somebody lost a moment ago.
 */
public final class LobbyRules {
	private LobbyRules() {
	}

	/** One second's work. */
	public static void tickSecond(Room room, LobbyState state, FreezeMuteConfig config, long now) {
		syncPresence(room, state);
		sweepGrace(room, state, config, now);
		returnEscapees(room);
		collectStrays(room, state, now);
		admit(room, state, config);
		updateBars(room, state);
	}

	/**
	 * Tells the state who is actually here, before anything decides what to do about it.
	 *
	 * <p>First, because every grace window below is measured from a timestamp, and a timestamp is
	 * only as honest as the events that set it. A crash records no disconnect for anybody, so the
	 * file comes back saying a roomful of people are online who are not; a player who reconnects
	 * has an entry that still says they left. Asking the room instead of trusting the bookkeeping
	 * costs one pass a second and means no window is ever counted down against somebody standing
	 * in the room.
	 */
	static void syncPresence(Room room, LobbyState state) {
		for (Occupant occupant : room.occupants()) {
			state.markPresent(occupant.uuid(), occupant.name());
		}
	}

	/**
	 * Drops entries whose grace window has run out.
	 *
	 * <p>Two windows meaning different things: a queued player who drops out keeps their place in
	 * line, an admitted one keeps their slot. Neither is touched while the player is connected.
	 */
	static void sweepGrace(Room room, LobbyState state, FreezeMuteConfig config, long now) {
		long queueGrace = config.lobbyQueueGraceMillis();
		long slotGrace = config.lobbySlotGraceMillis();

		for (Waiting entry : state.queue()) {
			if (entry.graceRanOut(now, queueGrace)) {
				state.dequeue(entry.uuid());
				room.log("Lobby: " + entry.name() + " lost their place in line after "
						+ queueGrace / 1000L + "s offline");
			}
		}

		for (Admitted entry : state.admitted()) {
			if (entry.graceRanOut(now, slotGrace)) {
				state.release(entry.uuid());
				room.log("Lobby: " + entry.name() + " lost their slot after " + slotGrace / 1000L + "s offline");
			}
		}
	}

	/**
	 * Puts back anybody the lobby is holding who is not in it any more.
	 *
	 * <p>A queued player has more ways out of that room than the interaction blocks cover: dying
	 * at all, {@code /kill}, and any teleport command another mod offers. Rather than naming each
	 * of those, where its members actually are is checked and they are walked back.
	 */
	static void returnEscapees(Room room) {
		if (!room.built()) {
			// Nowhere to walk them back to; sending them would only apologise, once a second.
			return;
		}

		for (Occupant occupant : room.occupants()) {
			if (!occupant.member() || occupant.inLobby()) {
				continue;
			}

			if (occupant.staff()) {
				// Made an operator while they happened to be out of the room. Walking them back in
				// would only give the stray collector something to walk straight out again a
				// moment later - one teleport there and one back, for nothing. It lets them go
				// here instead, which is the same ending without the round trip.
				room.log("Lobby: " + occupant.name() + " is staff now, so the lobby has stopped holding them");
				occupant.letOut();
				continue;
			}

			room.log("Lobby: " + occupant.name() + " left the lobby without being let in, putting them back");
			occupant.sendToLobby();
		}
	}

	/**
	 * Sorts out anybody standing in the room who the lobby is not holding correctly.
	 *
	 * <p>This is what makes the panic button safe - after everybody is pulled back the line can be
	 * rebuilt without asking anybody to reconnect - and it is what picks up somebody who was made
	 * an operator, or stopped being one, while they were standing there.
	 */
	static void collectStrays(Room room, LobbyState state, long now) {
		for (Occupant occupant : room.occupants()) {
			if (!occupant.inLobby()) {
				continue;
			}

			if (occupant.staff()) {
				if (occupant.member()) {
					// Somebody made an operator while they were waiting. Only on that change, so
					// staff who walked in with /lobby to look at the room are left alone.
					//
					// They go out rather than merely stopping being a member. Left standing there
					// they would keep a place in a line they can no longer be let through, stay in
					// adventure mode and invulnerable with nothing to undo it, and be invisible to
					// everybody else in the room for the rest of the session: the packet that
					// introduces two members is refused while both are members, and vanilla does
					// not offer it a second time. Walking out of the dimension is what makes every
					// client involved learn the room again.
					state.dequeue(occupant.uuid());
					state.release(occupant.uuid());
					room.log("Lobby: " + occupant.name() + " is staff now, so they are out of the line "
							+ "and out of the room");
					occupant.letOut();
				}

				continue;
			}

			if (!occupant.member()) {
				occupant.becomeMember();
			}

			if (!state.joinedAtAPoint() && !state.isAdmitted(occupant.uuid())
					&& state.waiting(occupant.uuid()) == null) {
				// Only when there is nowhere to ask. With a queue point set, standing in the room
				// is not the same as wanting a place in the line.
				state.enqueue(occupant.uuid(), occupant.name(), now);
			}
		}
	}

	/** Lets people through, oldest first, never more than the throttle allows. */
	static void admit(Room room, LobbyState state, FreezeMuteConfig config) {
		if (!state.queueOpen()) {
			return;
		}

		int budget = Math.max(1, config.lobbyAdmitPerSecond);
		Set<UUID> letThrough = new HashSet<>();

		while (budget > 0) {
			if (!LobbyManager.hasFreeSlot(state)) {
				return;
			}

			Waiting next = state.nextOnline();

			if (next == null) {
				return;
			}

			if (!letThrough.add(next.uuid())) {
				// Letting somebody through is supposed to take them out of the line. Seeing the
				// same person at the front twice in one pass means it did not, and carrying on
				// would spin here until the server gave up - so it stops instead, having done
				// nothing worse than let one fewer person in this second.
				room.log("Lobby: " + next.name() + " is still at the front of the line after being let "
						+ "through, so admissions are paused for this tick");
				return;
			}

			Occupant occupant = room.occupant(next.uuid());

			if (occupant == null) {
				// The entry says online but nobody is there; correct it and try the next one.
				state.markWaitingOffline(next.uuid(), System.currentTimeMillis());
				continue;
			}

			occupant.admit(true);
			budget--;
		}
	}

	/** Refreshes the bar of everybody still waiting, and takes away everybody else's. */
	static void updateBars(Room room, LobbyState state) {
		List<Waiting> queue = state.queue();
		int total = queue.size();
		boolean open = state.queueOpen();
		Set<UUID> waiting = new HashSet<>();

		for (int index = 0; index < total; index++) {
			Waiting entry = queue.get(index);
			waiting.add(entry.uuid());

			if (!entry.online()) {
				continue;
			}

			Occupant occupant = room.occupant(entry.uuid());

			if (occupant != null) {
				occupant.showBar(index + 1, total, open);
			}
		}

		room.dropBarsExcept(waiting);
	}
}
