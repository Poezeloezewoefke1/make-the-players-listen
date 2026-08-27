package dev.mtpl.freezemute.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.server.command.ServerCommandSource;

/** Entry point used by {@code CommandManagerMixin}. */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		FreezeCommand.register(dispatcher);
		MuteCommand.register(dispatcher);
		KitCommand.register(dispatcher);
		VoiceCommand.register(dispatcher);
		QueueCommand.register(dispatcher);
		LobbyCommand.register(dispatcher);
	}
}
