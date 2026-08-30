package dev.mtpl.freezemute;

/**
 * Whether a chat message reaches one particular person.
 *
 * <p>Chat is stopped where it is handed to a receiver rather than where it arrives from a sender,
 * so this is asked once per receiver and can answer differently for each of them. That is what
 * lets the lobby be quiet without being soundproof.
 *
 * <p>Written here, as a rule about three booleans, rather than as conditions threaded through a
 * mixin - the same reason {@code LobbyManager.hides} exists. The last time a rule like this was
 * only inferable from the code it was wrong, and the mod told players something untrue for weeks.
 */
public final class ChatDelivery {
	private ChatDelivery() {
	}

	/**
	 * True when this message should not be delivered to this receiver.
	 *
	 * <p>A mute is a punishment: it applies to every receiver, staff included, or it would be a
	 * punishment somebody could talk their way around. The lobby is not a punishment - it is a
	 * waiting room - so the people running the server can hear somebody in it asking for help,
	 * which is what the room tells them when their message disappears.
	 */
	public static boolean drops(boolean senderIsMuted, boolean senderIsInLobby, boolean receiverIsStaff) {
		if (senderIsMuted) {
			return true;
		}

		return senderIsInLobby && !receiverIsStaff;
	}
}
