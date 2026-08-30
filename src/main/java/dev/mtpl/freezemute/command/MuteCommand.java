package dev.mtpl.freezemute.command;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.ModerationData;
import dev.mtpl.freezemute.ModerationData.MuteEntry;
import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.util.Durations;
import dev.mtpl.freezemute.util.Messages;
import dev.mtpl.freezemute.util.StaffAlerts;

import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** {@code /mute}, {@code /unmute}, {@code /mutelist} and {@code /unmuteall}. */
public final class MuteCommand {
	private static final List<String> DURATION_SUGGESTIONS = List.of("5m", "30m", "1h", "12h", "1d", "7d", "perm");

	private MuteCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("mute")
				.requires(source -> Permissions.check(source, Permissions.MUTE))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(context -> mute(
								context.getSource(),
								EntityArgumentType.getPlayers(context, "targets"),
								Durations.PERMANENT,
								""))
						.then(CommandManager.argument("duration", StringArgumentType.word())
								.suggests(MuteCommand::suggestDurations)
								.executes(context -> mute(
										context.getSource(),
										EntityArgumentType.getPlayers(context, "targets"),
										StringArgumentType.getString(context, "duration"),
										""))
								.then(CommandManager.argument("reason", StringArgumentType.greedyString())
										.executes(context -> mute(
												context.getSource(),
												EntityArgumentType.getPlayers(context, "targets"),
												StringArgumentType.getString(context, "duration"),
												StringArgumentType.getString(context, "reason")))))));

		dispatcher.register(CommandManager.literal("unmute")
				.requires(source -> Permissions.check(source, Permissions.UNMUTE))
				.then(CommandManager.argument("player", StringArgumentType.word())
						.suggests(MuteCommand::suggestMuted)
						.executes(context -> unmute(
								context.getSource(),
								StringArgumentType.getString(context, "player")))));

		dispatcher.register(CommandManager.literal("mutelist")
				.requires(source -> Permissions.check(source, Permissions.LIST))
				.executes(context -> list(context.getSource())));

		dispatcher.register(CommandManager.literal("unmuteall")
				.requires(source -> Permissions.check(source, Permissions.UNMUTE))
				.executes(context -> unmuteAll(context.getSource())));
	}

	private static int mute(ServerCommandSource source, Collection<ServerPlayerEntity> targets, String duration, String reason) {
		long millis = Durations.parseMillis(duration);

		if (millis == Durations.INVALID) {
			source.sendError(Messages.failure(
					"'" + duration + "' is not a duration. Use for example 30s, 10m, 2h, 7d, 1h30m or perm."));
			return 0;
		}

		return mute(source, targets, millis, reason);
	}

	private static int mute(ServerCommandSource source, Collection<ServerPlayerEntity> targets, long durationMillis, String reason) {
		FreezeMute.rememberServer(source.getServer());

		ModerationData data = ModerationData.get();
		String actor = source.getName();
		long now = System.currentTimeMillis();
		long until = durationMillis == Durations.PERMANENT ? Durations.PERMANENT : now + durationMillis;
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			String name = target.getGameProfile().name();
			boolean wasMuted = data.isMuted(target.getUuid());
			MuteEntry entry = new MuteEntry(target.getUuid(), name, actor, now, until, reason);
			data.mute(entry);
			StaffAlerts.forget(target.getUuid());
			target.sendMessage(Messages.youAreMuted(entry));

			String feedback = (wasMuted ? "Updated the mute of " : "Muted ") + name + " " + Messages.describeRemaining(entry.until())
					+ (reason.isBlank() ? "" : " - reason: " + reason);
			source.sendFeedback(() -> Messages.success(feedback), true);
			count++;
		}

		return count;
	}

	private static int unmute(ServerCommandSource source, String name) {
		ModerationData data = ModerationData.get();
		MuteEntry entry = data.findMutedByName(name);

		if (entry == null) {
			source.sendError(Messages.failure(name + " is not muted."));
			return 0;
		}

		data.unmute(entry.uuid());
		StaffAlerts.forget(entry.uuid());
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(entry.uuid());

		if (player != null) {
			player.sendMessage(Messages.youAreUnmuted(LobbyManager.isMember(player)));
		}

		source.sendFeedback(() -> Messages.success("Unmuted " + entry.name()), true);
		return 1;
	}

	private static int list(ServerCommandSource source) {
		List<MuteEntry> entries = ModerationData.get().muteEntries();

		if (entries.isEmpty()) {
			source.sendFeedback(() -> Messages.header("Nobody is muted."), false);
			return 0;
		}

		source.sendFeedback(() -> Messages.header("Muted players (" + entries.size() + "):"), false);

		for (MuteEntry entry : entries) {
			boolean online = source.getServer().getPlayerManager().getPlayer(entry.uuid()) != null;
			String line = " - " + entry.name() + (online ? "" : " (offline)")
					+ " - muted " + Messages.describeRemaining(entry.until()) + " by " + entry.source()
					+ (entry.reason().isBlank() ? "" : " - reason: " + entry.reason());
			source.sendFeedback(() -> Messages.listEntry(line), false);
		}

		return entries.size();
	}

	private static int unmuteAll(ServerCommandSource source) {
		ModerationData data = ModerationData.get();
		List<MuteEntry> entries = data.muteEntries();
		int count = data.clearMuted();

		for (MuteEntry entry : entries) {
			ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(entry.uuid());

			if (player != null) {
				player.sendMessage(Messages.youAreUnmuted(LobbyManager.isMember(player)));
			}
		}

		source.sendFeedback(() -> Messages.success("Unmuted " + count + " player(s)"), true);
		return count;
	}

	private static CompletableFuture<Suggestions> suggestMuted(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(ModerationData.get().knownNames(false, true), builder);
	}

	private static CompletableFuture<Suggestions> suggestDurations(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(DURATION_SUGGESTIONS, builder);
	}
}
