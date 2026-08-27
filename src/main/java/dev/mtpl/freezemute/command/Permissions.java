package dev.mtpl.freezemute.command;

import java.lang.reflect.Method;

import dev.mtpl.freezemute.FreezeMute;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Who is allowed to freeze and mute.
 *
 * <p>Operators always are, along with the console, RCON and command blocks. If
 * fabric-permissions-api is installed - LuckPerms, player_roles and friends expose their
 * permissions through it - the nodes below are honoured as well, so a moderator rank can use
 * these commands without being a full operator. The API is optional and looked up reflectively:
 * without it the mod simply falls back to operator status.
 */
public final class Permissions {
	public static final String FREEZE = "freezemute.freeze";
	public static final String UNFREEZE = "freezemute.unfreeze";
	public static final String MUTE = "freezemute.mute";
	public static final String UNMUTE = "freezemute.unmute";
	public static final String LIST = "freezemute.list";
	public static final String KITGIVE = "freezemute.kitgive";
	public static final String VC_MUTE = "freezemute.vcmute";
	public static final String VC_UNMUTE = "freezemute.vcunmute";
	public static final String VC_DEAFEN = "freezemute.vcdeafen";
	public static final String VC_UNDEAFEN = "freezemute.vcundeafen";
	public static final String VC_LIST = "freezemute.vclist";
	public static final String QUEUE = "freezemute.queue";
	public static final String LOBBY = "freezemute.lobby";
	public static final String LOBBY_COURSE = "freezemute.lobby.course";
	/** Held by a player rather than checked on a command: whoever has it never sees the queue. */
	public static final String EARLY_ACCESS = "freezemute.lobby.early";
	/** Receives the "player is testing their punishment" messages. */
	public static final String STAFF = "freezemute.staff";

	private static final String API_MOD_ID = "fabric-permissions-api-v0";
	private static final Method SOURCE_CHECK = findCheck(ServerCommandSource.class);
	private static final Method PLAYER_CHECK = findCheck(ServerPlayerEntity.class);

	private Permissions() {
	}

	/** Logged once at startup so it is obvious which mode is in use. */
	public static void logMode() {
		if (SOURCE_CHECK != null) {
			FreezeMute.LOGGER.info("fabric-permissions-api found - permission nodes such as {} are honoured", FREEZE);
		} else if (FabricLoader.getInstance().isModLoaded(API_MOD_ID)) {
			FreezeMute.LOGGER.info("fabric-permissions-api is installed but its check method was not recognised, "
					+ "falling back to operator status");
		} else {
			FreezeMute.LOGGER.info("No permission mod found - the commands are for operators, the console and RCON");
		}
	}

	/** True when the source may use the command guarded by this node. */
	public static boolean check(ServerCommandSource source, String node) {
		if (!source.isExecutedByPlayer()) {
			// Console, RCON, command blocks and functions already run with elevated rights.
			return true;
		}

		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			return true;
		}

		if (SOURCE_CHECK != null) {
			try {
				return (boolean) SOURCE_CHECK.invoke(null, source, node, 2);
			} catch (ReflectiveOperationException | ClassCastException exception) {
				// Fall through to the operator check.
			}
		}

		return isOperator(source.getServer(), player);
	}

	/** True for players who should see the staff notifications. */
	public static boolean isStaff(ServerPlayerEntity player) {
		if (PLAYER_CHECK != null) {
			try {
				return (boolean) PLAYER_CHECK.invoke(null, player, STAFF, 2);
			} catch (ReflectiveOperationException | ClassCastException exception) {
				// Fall through to the operator check.
			}
		}

		MinecraftServer server = FreezeMute.server();
		return server != null && isOperator(server, player);
	}

	/**
	 * True for players who skip the queue.
	 *
	 * <p>Unlike {@link #isStaff}, this does not fall back to operator status: without a permission
	 * mod the node cannot be granted to anybody, and the early access list is the way in. Staff are
	 * waved through separately, so nothing is lost.
	 */
	public static boolean hasEarlyAccess(ServerPlayerEntity player) {
		if (PLAYER_CHECK == null) {
			return false;
		}

		try {
			return (boolean) PLAYER_CHECK.invoke(null, player, EARLY_ACCESS, 2);
		} catch (ReflectiveOperationException | ClassCastException exception) {
			return false;
		}
	}

	private static boolean isOperator(MinecraftServer server, ServerPlayerEntity player) {
		return server.getPlayerManager().isOperator(player.getPlayerConfigEntry());
	}

	private static Method findCheck(Class<?> subject) {
		if (!FabricLoader.getInstance().isModLoaded(API_MOD_ID)) {
			return null;
		}

		try {
			Class<?> permissions = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
			Method method = permissions.getMethod("check", subject, String.class, int.class);
			return java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? method : null;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}
	}
}
