package dev.mtpl.freezemute.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Who is allowed to freeze and mute: operators, the console, RCON and command blocks. */
public final class Permissions {
	private Permissions() {
	}

	public static boolean isModerator(ServerCommandSource source) {
		if (!source.isExecutedByPlayer()) {
			// Console, RCON, command blocks and functions already run with elevated rights.
			return true;
		}

		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			return true;
		}

		return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
	}
}
