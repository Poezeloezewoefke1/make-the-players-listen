package dev.mtpl.freezemute;

/**
 * Implemented by the {@code ServerPlayNetworkHandler} mixin.
 *
 * <p>Snapping a player back is done from inside the network handler itself, so the mod never
 * has to look up the player's world or the server to correct a position. That keeps it off the
 * parts of the Minecraft API that get renamed most often.
 */
public interface FrozenConnection {
	/** Sends the client back to the position and view angles the server holds. Server thread only. */
	void freezemute$snapBack();
}
