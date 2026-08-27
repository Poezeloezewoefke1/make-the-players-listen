package dev.mtpl.freezemute.command;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.mtpl.freezemute.util.Durations;
import dev.mtpl.freezemute.util.Messages;
import dev.mtpl.freezemute.voice.VoiceData;
import dev.mtpl.freezemute.voice.VoiceData.Kind;
import dev.mtpl.freezemute.voice.VoiceData.VoiceEntry;
import dev.mtpl.freezemute.voice.VoiceSupport;

import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * The voice chat commands: {@code /vcmute}, {@code /vcunmute}, {@code /vcdeafen},
 * {@code /vcundeafen}, {@code /vclist}, {@code /vcinfo} and {@code /vcstatus}.
 *
 * <p>All of them are for operators only, the same as the rest of the mod, and all of them are
 * registered whether or not Simple Voice Chat is installed - a punishment set on a server that is
 * missing the voice chat mod is still remembered and takes effect the moment it is put back.
 * {@code /vcstatus} says which of the two situations you are in.
 */
public final class VoiceCommand {
	private static final List<String> DURATION_SUGGESTIONS = List.of("5m", "30m", "1h", "12h", "1d", "7d", "perm");

	private VoiceCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(punish("vcmute", Kind.MUTE, Permissions.VC_MUTE));
		dispatcher.register(punish("vcdeafen", Kind.DEAFEN, Permissions.VC_DEAFEN));
		dispatcher.register(lift("vcunmute", Kind.MUTE, Permissions.VC_UNMUTE));
		dispatcher.register(lift("vcundeafen", Kind.DEAFEN, Permissions.VC_UNDEAFEN));

		dispatcher.register(CommandManager.literal("vclist")
				.requires(source -> Permissions.check(source, Permissions.VC_LIST))
				.executes(context -> list(context.getSource())));

		dispatcher.register(CommandManager.literal("vcinfo")
				.requires(source -> Permissions.check(source, Permissions.VC_LIST))
				.then(CommandManager.argument("player", StringArgumentType.word())
						.suggests(VoiceCommand::suggestEveryone)
						.executes(context -> info(context.getSource(),
								StringArgumentType.getString(context, "player")))));

		dispatcher.register(CommandManager.literal("vcstatus")
				.requires(source -> Permissions.check(source, Permissions.VC_LIST))
				.executes(context -> status(context.getSource())));
	}

	/** {@code /vcmute <player> [duration] [reason]} - the duration is optional and means forever. */
	private static LiteralArgumentBuilder<ServerCommandSource> punish(String name, Kind kind, String node) {
		return CommandManager.literal(name)
				.requires(source -> Permissions.check(source, node))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(context -> apply(context.getSource(), kind,
								EntityArgumentType.getPlayers(context, "targets"), Durations.PERMANENT, ""))
						.then(CommandManager.argument("duration", StringArgumentType.word())
								.suggests(VoiceCommand::suggestDurations)
								.executes(context -> apply(context.getSource(), kind,
										EntityArgumentType.getPlayers(context, "targets"),
										StringArgumentType.getString(context, "duration"), ""))
								.then(CommandManager.argument("reason", StringArgumentType.greedyString())
										.executes(context -> apply(context.getSource(), kind,
												EntityArgumentType.getPlayers(context, "targets"),
												StringArgumentType.getString(context, "duration"),
												StringArgumentType.getString(context, "reason"))))));
	}

	/** {@code /vcunmute <player>} - takes a name, so it works while the player is offline. */
	private static LiteralArgumentBuilder<ServerCommandSource> lift(String name, Kind kind, String node) {
		return CommandManager.literal(name)
				.requires(source -> Permissions.check(source, node))
				.then(CommandManager.argument("player", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(
								VoiceData.get().names(kind), builder))
						.executes(context -> lift(context.getSource(), kind,
								StringArgumentType.getString(context, "player"))))
				.then(CommandManager.literal("all")
						.executes(context -> liftAll(context.getSource(), kind)));
	}

	private static int apply(ServerCommandSource source, Kind kind, Collection<ServerPlayerEntity> targets,
			String duration, String reason) {
		long millis = Durations.parseMillis(duration);

		if (millis == Durations.INVALID) {
			source.sendError(Messages.failure("'" + duration + "' is not a duration. Try 30m, 2h, 7d, 1h30m or perm."));
			return 0;
		}

		return apply(source, kind, targets, millis, reason);
	}

	private static int apply(ServerCommandSource source, Kind kind, Collection<ServerPlayerEntity> targets,
			long millis, String reason) {
		long now = System.currentTimeMillis();
		long until = millis == Durations.PERMANENT ? Durations.PERMANENT : now + millis;
		String by = source.getName();
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			VoiceEntry entry = new VoiceEntry(target.getUuid(), target.getGameProfile().name(), by, now, until, reason);
			boolean fresh = VoiceData.get().apply(kind, entry);

			target.sendMessage(Text.literal("You are " + kind.past() + " in voice chat "
					+ Messages.describeRemaining(until) + describeReason(reason)));

			String verb = fresh ? "Voice " + kind.past() : "Updated the voice " + kind.past() + " for";
			String name = entry.name();
			source.sendFeedback(() -> Messages.success(verb + " " + name + " "
					+ Messages.describeRemaining(until) + describeReason(reason)), true);
			count++;
		}

		if (count > 0 && !VoiceSupport.installed()) {
			source.sendFeedback(() -> Messages.failure("Note: Simple Voice Chat is not installed, so this does "
					+ "nothing until it is. The punishment is remembered."), false);
		}

		return count;
	}

	private static int lift(ServerCommandSource source, Kind kind, String name) {
		VoiceEntry entry = VoiceData.get().findByName(kind, name);

		if (entry == null) {
			source.sendError(Messages.failure(name + " is not " + kind.past() + " in voice chat."));
			return 0;
		}

		VoiceData.get().lift(kind, entry.uuid());
		source.sendFeedback(() -> Messages.success("Lifted the voice " + kind.past() + " on " + entry.name()), true);
		notifyIfOnline(source, kind, entry);
		return 1;
	}

	private static int liftAll(ServerCommandSource source, Kind kind) {
		List<VoiceEntry> lifted = VoiceData.get().entries(kind);
		int count = VoiceData.get().clear(kind);

		for (VoiceEntry entry : lifted) {
			notifyIfOnline(source, kind, entry);
		}

		source.sendFeedback(() -> Messages.success("Lifted the voice " + kind.past() + " on " + count + " player(s)"), true);
		return count;
	}

	private static void notifyIfOnline(ServerCommandSource source, Kind kind, VoiceEntry entry) {
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(entry.uuid());

		if (player != null) {
			player.sendMessage(Text.literal("You " + kind.liftedText() + "."));
		}
	}

	private static int list(ServerCommandSource source) {
		List<VoiceEntry> mutes = VoiceData.get().entries(Kind.MUTE);
		List<VoiceEntry> deafens = VoiceData.get().entries(Kind.DEAFEN);

		if (mutes.isEmpty() && deafens.isEmpty()) {
			source.sendFeedback(() -> Messages.header("Nobody is muted or deafened in voice chat."), false);
			return 0;
		}

		section(source, "Voice muted", mutes);
		section(source, "Voice deafened", deafens);
		return mutes.size() + deafens.size();
	}

	private static void section(ServerCommandSource source, String title, List<VoiceEntry> entries) {
		if (entries.isEmpty()) {
			return;
		}

		source.sendFeedback(() -> Messages.header(title + " (" + entries.size() + "):"), false);

		for (VoiceEntry entry : entries) {
			source.sendFeedback(() -> Messages.listEntry("  " + entry.name() + " - "
					+ Messages.describeRemaining(entry.until()) + ", by " + entry.source()
					+ describeReason(entry.reason())), false);
		}
	}

	private static int info(ServerCommandSource source, String name) {
		VoiceEntry mute = VoiceData.get().findByName(Kind.MUTE, name);
		VoiceEntry deafen = VoiceData.get().findByName(Kind.DEAFEN, name);

		source.sendFeedback(() -> Messages.header("Voice chat status for " + name + ":"), false);
		line(source, "muted", mute);
		line(source, "deafened", deafen);
		return mute == null && deafen == null ? 0 : 1;
	}

	private static void line(ServerCommandSource source, String label, VoiceEntry entry) {
		if (entry == null) {
			source.sendFeedback(() -> Messages.listEntry("  not " + label), false);
			return;
		}

		source.sendFeedback(() -> Messages.listEntry("  " + label + " " + Messages.describeRemaining(entry.until())
				+ ", by " + entry.source() + describeReason(entry.reason())), false);
	}

	private static int status(ServerCommandSource source) {
		if (!VoiceSupport.installed()) {
			source.sendFeedback(() -> Messages.failure("Simple Voice Chat is not installed. The commands still "
					+ "work and remember who is punished, but nothing is enforced until you install it."), false);
			return 0;
		}

		if (!VoiceSupport.active()) {
			source.sendFeedback(() -> Messages.failure("Simple Voice Chat is installed but has not loaded this "
					+ "mod's plugin. Check the server log at startup for the reason."), false);
			return 0;
		}

		source.sendFeedback(() -> Messages.success("Simple Voice Chat is installed and the plugin is active - "
				+ VoiceData.get().entries(Kind.MUTE).size() + " muted, "
				+ VoiceData.get().entries(Kind.DEAFEN).size() + " deafened."), false);
		return 1;
	}

	private static String describeReason(String reason) {
		return reason == null || reason.isBlank() ? "" : " - reason: " + reason;
	}

	private static CompletableFuture<Suggestions> suggestDurations(CommandContext<ServerCommandSource> context,
			SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(DURATION_SUGGESTIONS, builder);
	}

	private static CompletableFuture<Suggestions> suggestEveryone(CommandContext<ServerCommandSource> context,
			SuggestionsBuilder builder) {
		List<String> names = VoiceData.get().names(Kind.MUTE);
		names.addAll(VoiceData.get().names(Kind.DEAFEN));

		for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
			names.add(player.getGameProfile().name());
		}

		names.sort(String.CASE_INSENSITIVE_ORDER);
		return CommandSource.suggestMatching(names, builder);
	}
}
