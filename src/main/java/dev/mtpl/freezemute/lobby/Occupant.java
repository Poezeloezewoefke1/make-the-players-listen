package dev.mtpl.freezemute.lobby;

import java.util.UUID;

/**
 * One player, as the lobby's rules see them.
 *
 * <p>Everything the once-a-second pass does to somebody goes through here, and nothing in it
 * mentions Minecraft. That is the whole point: the decisions this drives - who is walked back to
 * the room, who is let through, whose place has run out, whose boss bar goes away - are the ones
 * every bug found in the lobby so far has been in, and until there was a seam like this none of
 * them could be tested without a running server and two people standing in it.
 */
public interface Occupant {
	UUID uuid();

	String name();

	/** Staff are never queued, never held, and never take up a slot. */
	boolean staff();

	/** Whether they are standing in the lobby dimension right now. */
	boolean inLobby();

	/** Whether the lobby is currently holding them by its rules. */
	boolean member();

	/** Move them into the lobby and apply the member rules. */
	void sendToLobby();

	/**
	 * Let them through into the world.
	 *
	 * <p>This has to take them out of the line and give them a slot as well as moving them - the
	 * pass that calls it works out who is next by asking the line, so an implementation that
	 * moved somebody without dequeuing them would be asked to move them again immediately.
	 */
	void admit(boolean announce);

	/** Apply the member rules without moving them. */
	void becomeMember();

	/** Drop the member rules without moving them. */
	void stopBeingMember();

	void showBar(int position, int total, boolean open);

	void message(String text);
}
