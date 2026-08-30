package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A lobby with nobody real in it.
 *
 * <p>Stands in for a running server so the once-a-second rules can be driven straight through:
 * people arrive, wander off, disconnect, are made operators and stop being operators, and every
 * decision the lobby takes about them is recorded and can be asserted on.
 */
final class FakeRoom implements Room {
	private final Map<UUID, FakePlayer> players = new LinkedHashMap<>();
	final List<String> log = new ArrayList<>();
	boolean built = true;

	FakePlayer add(String name) {
		FakePlayer player = new FakePlayer(this, new UUID(0L, players.size() + 1), name);
		players.put(player.uuid(), player);
		return player;
	}

	void disconnect(FakePlayer player) {
		players.remove(player.uuid());
	}

	@Override
	public List<Occupant> occupants() {
		return new ArrayList<>(players.values());
	}

	@Override
	public Occupant occupant(UUID uuid) {
		return players.get(uuid);
	}

	@Override
	public boolean built() {
		return built;
	}

	@Override
	public void dropBarsExcept(Set<UUID> keep) {
		for (FakePlayer player : players.values()) {
			if (!keep.contains(player.uuid())) {
				player.bar = null;
			}
		}
	}

	@Override
	public void log(String message) {
		log.add(message);
	}

	/** What the lobby did to one person, and what it can see about them. */
	static final class FakePlayer implements Occupant {
		private final FakeRoom room;
		private final UUID uuid;
		private String name;

		boolean staff;
		boolean inLobby;
		boolean member;
		String bar;
		int admitted;
		int sentToLobby;
		final List<String> messages = new ArrayList<>();

		FakePlayer(FakeRoom room, UUID uuid, String name) {
			this.room = room;
			this.uuid = uuid;
			this.name = name;
		}

		FakePlayer staff(boolean value) {
			staff = value;
			return this;
		}

		FakePlayer standingInTheLobby() {
			inLobby = true;
			return this;
		}

		FakePlayer rename(String value) {
			name = value;
			return this;
		}

		/** What a teleport out of the room does, from the room's point of view. */
		void wanderOff() {
			inLobby = false;
		}

		@Override
		public UUID uuid() {
			return uuid;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public boolean staff() {
			return staff;
		}

		@Override
		public boolean inLobby() {
			return inLobby;
		}

		@Override
		public boolean member() {
			return member;
		}

		@Override
		public void sendToLobby() {
			sentToLobby++;
			inLobby = true;

			if (!staff) {
				member = true;
			}
		}

		@Override
		public void admit(boolean announce) {
			// The same two state changes the real one makes. Without them the pass that calls
			// this would find the same person at the front of the line on its next turn.
			LobbyState.get().dequeue(uuid);
			LobbyState.get().admit(uuid, name, 0L);

			admitted++;
			inLobby = false;
			member = false;
			bar = null;
		}

		@Override
		public void becomeMember() {
			member = !staff;
		}

		@Override
		public void stopBeingMember() {
			member = false;
			bar = null;
		}

		@Override
		public void showBar(int position, int total, boolean open) {
			bar = (open ? "place " : "closed ") + position + " of " + total;
		}

		@Override
		public void message(String text) {
			messages.add(text);
		}
	}
}
