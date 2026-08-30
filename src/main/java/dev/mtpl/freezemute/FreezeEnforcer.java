package dev.mtpl.freezemute;

import java.lang.reflect.Method;

import dev.mtpl.freezemute.lobby.LobbyManager;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Keeps a frozen player exactly where the server thinks they are.
 *
 * <p>The server stays authoritative about a frozen player's position only because it refuses
 * their movement packets (see {@code ServerPlayNetworkHandlerMixin}). This class takes care of
 * the other half: telling the client to go back whenever it drifts away.
 */
public final class FreezeEnforcer {
	/** A client that is standing still still jitters a tiny bit; ignore that. */
	private static final double POSITION_TOLERANCE = 1.0E-4D;
	private static final float ROTATION_TOLERANCE = 0.05F;

	/**
	 * {@code ServerPlayNetworkHandler#resetFloatingTicks()}. Vanilla disconnects players that
	 * seem to hover in mid-air; a player frozen while falling or flying would trip that check,
	 * so the counter is reset while the freeze is active. Looked up reflectively: if a future
	 * Minecraft build drops the method the freeze still works, it just loses this safety net.
	 */
	private static final Method RESET_FLOATING_TICKS = findResetFloatingTicks();

	private FreezeEnforcer() {
	}

	/** Called when a freeze starts: get the player out of any vehicle and pin them down. */
	public static void onFrozen(ServerPlayerEntity player) {
		if (player.hasVehicle()) {
			player.stopRiding();
		}

		if (FreezeMuteConfig.get().freezeProtectsFromDamage) {
			// A player being held for questioning should not drown or get shot in the meantime.
			player.setInvulnerable(true);
		}

		snapBack(player);
	}

	/** Called when a freeze ends, restoring whatever invulnerability the player had before. */
	public static void onUnfrozen(ServerPlayerEntity player, ModerationData.FreezeEntry entry) {
		player.setInvulnerable(invulnerableAfterFreeze(LobbyManager.isMember(player),
				entry != null && entry.wasInvulnerable()));
	}

	/**
	 * Whether a player is still invulnerable once a freeze is lifted.
	 *
	 * <p>Two things switch this on and neither may switch off the other's. The lobby protects its
	 * members because nobody should die waiting in line; a freeze protects its subject so somebody
	 * held for questioning does not drown while being questioned. Lifting one hands back only what
	 * the player had before either of them got hold of them - and if the other is still holding
	 * them, it keeps holding them.
	 */
	public static boolean invulnerableAfterFreeze(boolean isLobbyMember, boolean hadItBefore) {
		return isLobbyMember || hadItBefore;
	}

	/**
	 * What to write down as a player's own invulnerability when a freeze starts.
	 *
	 * <p>A lobby member is invulnerable because the lobby is holding them, not because anybody
	 * decided they should be. Recording that as theirs would hand it back on {@code /unfreeze} and
	 * leave them walking around the world unkillable for good.
	 */
	public static boolean ownInvulnerability(boolean invulnerableNow, boolean isLobbyMember) {
		return invulnerableNow && !isLobbyMember;
	}

	/** Re-applies the freeze to a player who just logged in. */
	public static void onRejoinedWhileFrozen(ServerPlayerEntity player) {
		if (FreezeMuteConfig.get().freezeProtectsFromDamage) {
			player.setInvulnerable(true);
		}
	}

	/**
	 * Sends the player back to the position and the view angles the server holds for them.
	 * Must run on the server thread.
	 */
	public static void snapBack(ServerPlayerEntity player) {
		ServerPlayNetworkHandler handler = player.networkHandler;

		if (handler != null) {
			((FrozenConnection) (Object) handler).freezemute$snapBack();
		}
	}

	/** True when the client reports a position or rotation that the server did not authorise. */
	public static boolean hasDrifted(ServerPlayerEntity player, double x, double y, double z, float yaw, float pitch) {
		if (!Double.isNaN(x)
				&& (Math.abs(x - player.getX()) > POSITION_TOLERANCE
				|| Math.abs(y - player.getY()) > POSITION_TOLERANCE
				|| Math.abs(z - player.getZ()) > POSITION_TOLERANCE)) {
			return true;
		}

		return !Float.isNaN(yaw)
				&& (angleDifference(yaw, player.getYaw()) > ROTATION_TOLERANCE
				|| Math.abs(pitch - player.getPitch()) > ROTATION_TOLERANCE);
	}

	/** Stops vanilla's "you seem to be floating" disconnect from firing at a frozen player. */
	public static void resetFloatingTicks(ServerPlayNetworkHandler handler) {
		if (RESET_FLOATING_TICKS == null) {
			return;
		}

		try {
			RESET_FLOATING_TICKS.invoke(handler);
		} catch (ReflectiveOperationException exception) {
			// Nothing useful to do here; the freeze itself is unaffected.
		}
	}

	private static float angleDifference(float first, float second) {
		float difference = (first - second) % 360.0F;

		if (difference >= 180.0F) {
			difference -= 360.0F;
		}

		if (difference < -180.0F) {
			difference += 360.0F;
		}

		return Math.abs(difference);
	}

	private static Method findResetFloatingTicks() {
		try {
			String name = FabricLoader.getInstance().getMappingResolver()
					.mapMethodName("intermediary", "net.minecraft.class_3244", "method_75005", "()V");
			Method method = ServerPlayNetworkHandler.class.getDeclaredMethod(name);
			method.setAccessible(true);
			return method;
		} catch (Throwable throwable) {
			FreezeMute.LOGGER.info(
					"Vanilla's floating-tick reset was not found ({}). Freezing still works; only the anti-fly kick safety net is off.",
					throwable.toString());
			return null;
		}
	}
}
