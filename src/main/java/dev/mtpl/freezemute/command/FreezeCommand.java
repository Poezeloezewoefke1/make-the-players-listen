package dev.mtpl.freezemute.command;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.mtpl.freezemute.FreezeEnforcer;
import dev.mtpl.freezemute.ModerationData;
import dev.mtpl.freezemute.ModerationData.FreezeEntry;
import dev.mtpl.freezemute.util.Messages;

import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** {@code /freeze}, {@code /unfreeze}, {@code /freezelist} and {@code /unfreezeall}. */
public final class FreezeCommand {
	private FreezeCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("freeze")
				.requires(Permissions::isModerator)
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(context -> freeze(
								context.getSource(),
								EntityArgumentType.getPlayers(context, "targets")))));

		dispatcher.register(CommandManager.literal("unfreeze")
				.requires(Permissions::isModerator)
				.then(CommandManager.argument("player", StringArgumentType.word())
						.suggests(FreezeCommand::suggestFrozen)
						.executes(context -> unfreeze(
								context.getSource(),
								StringArgumentType.getString(context, "player")))));

		dispatcher.register(CommandManager.literal("freezelist")
				.requires(Permissions::isModerator)
				.executes(context -> list(context.getSource())));

		dispatcher.register(CommandManager.literal("unfreezeall")
				.requires(Permissions::isModerator)
				.executes(context -> unfreezeAll(context.getSource())));
	}

	private static int freeze(ServerCommandSource source, Collection<ServerPlayerEntity> targets) {
		ModerationData data = ModerationData.get();
		String actor = source.getName();
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			String name = target.getGameProfile().getName();

			if (data.isFrozen(target.getUuid())) {
				source.sendError(Messages.failure(name + " is already frozen."));
				continue;
			}

			data.freeze(new FreezeEntry(target.getUuid(), name, actor, System.currentTimeMillis()));
			FreezeEnforcer.onFrozen(target);
			target.sendMessage(Messages.youAreFrozen(actor));
			source.sendFeedback(() -> Messages.success("Froze " + name), true);
			count++;
		}

		return count;
	}

	private static int unfreeze(ServerCommandSource source, String name) {
		ModerationData data = ModerationData.get();
		FreezeEntry entry = data.findFrozenByName(name);

		if (entry == null) {
			source.sendError(Messages.failure(name + " is not frozen."));
			return 0;
		}

		data.unfreeze(entry.uuid());
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(entry.uuid());

		if (player != null) {
			player.sendMessage(Messages.youAreUnfrozen());
		}

		source.sendFeedback(() -> Messages.success("Unfroze " + entry.name()), true);
		return 1;
	}

	private static int list(ServerCommandSource source) {
		List<FreezeEntry> entries = ModerationData.get().frozenEntries();

		if (entries.isEmpty()) {
			source.sendFeedback(() -> Messages.header("Nobody is frozen."), false);
			return 0;
		}

		source.sendFeedback(() -> Messages.header("Frozen players (" + entries.size() + "):"), false);

		for (FreezeEntry entry : entries) {
			boolean online = source.getServer().getPlayerManager().getPlayer(entry.uuid()) != null;
			String line = " - " + entry.name() + (online ? "" : " (offline)") + " - frozen by " + entry.source();
			source.sendFeedback(() -> Messages.listEntry(line), false);
		}

		return entries.size();
	}

	private static int unfreezeAll(ServerCommandSource source) {
		ModerationData data = ModerationData.get();
		List<FreezeEntry> entries = data.frozenEntries();
		int count = data.clearFrozen();

		for (FreezeEntry entry : entries) {
			ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(entry.uuid());

			if (player != null) {
				player.sendMessage(Messages.youAreUnfrozen());
			}
		}

		source.sendFeedback(() -> Messages.success("Unfroze " + count + " player(s)"), true);
		return count;
	}

	private static CompletableFuture<Suggestions> suggestFrozen(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(ModerationData.get().knownNames(true, false), builder);
	}
}
