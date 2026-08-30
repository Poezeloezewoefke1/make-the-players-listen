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
import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.ModerationData;
import dev.mtpl.freezemute.ModerationData.FreezeEntry;
import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.util.Durations;
import dev.mtpl.freezemute.util.Messages;
import dev.mtpl.freezemute.util.StaffAlerts;

import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** {@code /freeze}, {@code /unfreeze}, {@code /freezelist} and {@code /unfreezeall}. */
public final class FreezeCommand {
	private static final List<String> DURATION_SUGGESTIONS = List.of("5m", "30m", "1h", "12h", "1d", "perm");

	private FreezeCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("freeze")
				.requires(source -> Permissions.check(source, Permissions.FREEZE))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(context -> freeze(
								context.getSource(),
								EntityArgumentType.getPlayers(context, "targets"),
								Durations.PERMANENT,
								""))
						.then(CommandManager.argument("duration", StringArgumentType.word())
								.suggests(FreezeCommand::suggestDurations)
								.executes(context -> freeze(
										context.getSource(),
										EntityArgumentType.getPlayers(context, "targets"),
										StringArgumentType.getString(context, "duration"),
										""))
								.then(CommandManager.argument("reason", StringArgumentType.greedyString())
										.executes(context -> freeze(
												context.getSource(),
												EntityArgumentType.getPlayers(context, "targets"),
												StringArgumentType.getString(context, "duration"),
												StringArgumentType.getString(context, "reason")))))));

		dispatcher.register(CommandManager.literal("unfreeze")
				.requires(source -> Permissions.check(source, Permissions.UNFREEZE))
				.then(CommandManager.argument("player", StringArgumentType.word())
						.suggests(FreezeCommand::suggestFrozen)
						.executes(context -> unfreeze(
								context.getSource(),
								StringArgumentType.getString(context, "player")))));

		dispatcher.register(CommandManager.literal("freezelist")
				.requires(source -> Permissions.check(source, Permissions.LIST))
				.executes(context -> list(context.getSource())));

		dispatcher.register(CommandManager.literal("unfreezeall")
				.requires(source -> Permissions.check(source, Permissions.UNFREEZE))
				.executes(context -> unfreezeAll(context.getSource())));
	}

	private static int freeze(ServerCommandSource source, Collection<ServerPlayerEntity> targets, String duration, String reason) {
		long millis = Durations.parseMillis(duration);

		if (millis == Durations.INVALID) {
			source.sendError(Messages.failure(
					"'" + duration + "' is not a duration. Use for example 30s, 10m, 2h, 7d, 1h30m or perm."));
			return 0;
		}

		return freeze(source, targets, millis, reason);
	}

	private static int freeze(ServerCommandSource source, Collection<ServerPlayerEntity> targets, long durationMillis, String reason) {
		FreezeMute.rememberServer(source.getServer());

		ModerationData data = ModerationData.get();
		String actor = source.getName();
		long now = System.currentTimeMillis();
		long until = durationMillis == Durations.PERMANENT ? Durations.PERMANENT : now + durationMillis;
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			String name = target.getGameProfile().name();
			FreezeEntry existing = data.freezeOf(target.getUuid());

			// Keep the invulnerability the player had before the first freeze, not the one this
			// mod switched on, otherwise re-freezing would make it stick for good. The lobby's
			// counts as the mod's: somebody frozen while waiting in line, let in, and then
			// unfrozen would otherwise be unkillable out in the world from then on.
			boolean wasInvulnerable = existing != null ? existing.wasInvulnerable()
					: FreezeEnforcer.ownInvulnerability(target.isInvulnerable(), LobbyManager.isMember(target));

			FreezeEntry entry = new FreezeEntry(target.getUuid(), name, actor, now, until, reason, wasInvulnerable);
			data.freeze(entry);
			FreezeEnforcer.onFrozen(target);
			StaffAlerts.forget(target.getUuid());
			target.sendMessage(Messages.youAreFrozen(entry));

			String feedback = (existing != null ? "Updated the freeze on " : "Froze ") + name + " "
					+ Messages.describeRemaining(until) + (reason.isBlank() ? "" : " - reason: " + reason);
			source.sendFeedback(() -> Messages.success(feedback), true);
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
		release(source, entry);
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
			String line = " - " + entry.name() + (online ? "" : " (offline)")
					+ " - frozen " + Messages.describeRemaining(entry.until()) + " by " + entry.source()
					+ (entry.reason().isBlank() ? "" : " - reason: " + entry.reason());
			source.sendFeedback(() -> Messages.listEntry(line), false);
		}

		return entries.size();
	}

	private static int unfreezeAll(ServerCommandSource source) {
		ModerationData data = ModerationData.get();
		List<FreezeEntry> entries = data.frozenEntries();
		int count = data.clearFrozen();

		for (FreezeEntry entry : entries) {
			release(source, entry);
		}

		source.sendFeedback(() -> Messages.success("Unfroze " + count + " player(s)"), true);
		return count;
	}

	private static void release(ServerCommandSource source, FreezeEntry entry) {
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(entry.uuid());

		if (player != null) {
			FreezeEnforcer.onUnfrozen(player, entry);
			StaffAlerts.forget(entry.uuid());
			player.sendMessage(Messages.youAreUnfrozen());
		}
	}

	private static CompletableFuture<Suggestions> suggestFrozen(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(ModerationData.get().knownNames(true, false), builder);
	}

	private static CompletableFuture<Suggestions> suggestDurations(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(DURATION_SUGGESTIONS, builder);
	}
}
