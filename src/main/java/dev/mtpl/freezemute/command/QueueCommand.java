package dev.mtpl.freezemute.command;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.lobby.LobbyState;
import dev.mtpl.freezemute.lobby.LobbyState.Admitted;
import dev.mtpl.freezemute.lobby.LobbyState.Waiting;
import dev.mtpl.freezemute.util.Durations;
import dev.mtpl.freezemute.util.Messages;

import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * {@code /queue} - everything about the line.
 *
 * <p>Staff only, like the rest of the mod. Players never run any of this; the queue moves on its
 * own and tells them where they stand on a boss bar.
 */
public final class QueueCommand {
	private QueueCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("queue")
				.requires(source -> Permissions.check(source, Permissions.QUEUE))
				.executes(context -> status(context.getSource()))
				.then(CommandManager.literal("status")
						.executes(context -> status(context.getSource())))
				.then(CommandManager.literal("list")
						.executes(context -> list(context.getSource())))
				.then(CommandManager.literal("open")
						.executes(context -> setOpen(context.getSource(), true)))
				.then(CommandManager.literal("close")
						.executes(context -> setOpen(context.getSource(), false)))
				.then(CommandManager.literal("end")
						.executes(context -> end(context.getSource())))
				.then(CommandManager.literal("cap")
						.executes(context -> showCap(context.getSource()))
						.then(CommandManager.argument("slots", IntegerArgumentType.integer(0, 10000))
								.executes(context -> setCap(context.getSource(),
										IntegerArgumentType.getInteger(context, "slots")))))
				.then(CommandManager.literal("bypass")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(context -> bypass(context.getSource(),
										EntityArgumentType.getPlayers(context, "targets")))))
				.then(CommandManager.literal("early")
						.then(CommandManager.literal("list")
								.executes(context -> earlyList(context.getSource())))
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("targets", EntityArgumentType.players())
										.executes(context -> earlyAdd(context.getSource(),
												EntityArgumentType.getPlayers(context, "targets")))))
						.then(CommandManager.literal("remove")
								.then(CommandManager.argument("player", StringArgumentType.word())
										.suggests((context, builder) -> CommandSource.suggestMatching(
												LobbyState.get().earlyAccess().values(), builder))
										.executes(context -> earlyRemove(context.getSource(),
												StringArgumentType.getString(context, "player")))))));
	}

	private static int status(ServerCommandSource source) {
		LobbyState state = LobbyState.get();
		source.sendFeedback(() -> Messages.header("Queue"), false);
		source.sendFeedback(() -> Messages.listEntry("  lobby: " + (state.enabled() ? "on" : "off")), false);
		source.sendFeedback(() -> Messages.listEntry("  line: " + (state.queueOpen() ? "open" : "closed")
				+ ", " + state.queueSize() + " waiting"), false);
		source.sendFeedback(() -> Messages.listEntry("  slots: " + state.slotsUsed() + " used of "
				+ (state.cap() == 0 ? "unlimited" : state.cap())), false);
		source.sendFeedback(() -> Messages.listEntry("  in the lobby right now: " + LobbyManager.memberCount()), false);
		return 1;
	}

	private static int list(ServerCommandSource source) {
		LobbyState state = LobbyState.get();
		List<Waiting> queue = state.queue();
		List<Admitted> admitted = state.admitted();

		if (queue.isEmpty() && admitted.isEmpty()) {
			source.sendFeedback(() -> Messages.success("Nobody is waiting and nobody is in."), false);
			return 1;
		}

		long now = System.currentTimeMillis();
		source.sendFeedback(() -> Messages.header("In the world (" + admitted.size() + ")"), false);

		for (Admitted entry : admitted) {
			source.sendFeedback(() -> Messages.listEntry("  " + entry.name()
					+ (entry.online() ? "" : " - offline, slot held " + heldFor(entry.offlineSince(), now))), false);
		}

		source.sendFeedback(() -> Messages.header("Waiting (" + queue.size() + ")"), false);

		int place = 1;

		for (Waiting entry : queue) {
			int number = place++;
			source.sendFeedback(() -> Messages.listEntry("  " + number + ". " + entry.name()
					+ (entry.online() ? "" : " - offline, place held " + heldFor(entry.offlineSince(), now))), false);
		}

		return queue.size() + admitted.size();
	}

	private static String heldFor(long offlineSince, long now) {
		return "for " + Durations.format(Math.max(0L, now - offlineSince)) + " so far";
	}

	private static int setOpen(ServerCommandSource source, boolean open) {
		LobbyState.get().setQueueOpen(open);
		source.sendFeedback(() -> Messages.success(open
				? "The queue is open - people will be let through again."
				: "The queue is closed - nobody new gets let through."), true);
		return 1;
	}

	private static int showCap(ServerCommandSource source) {
		int cap = LobbyState.get().cap();
		source.sendFeedback(() -> Messages.success(cap == 0
				? "There is no cap - everybody gets let straight through."
				: "The cap is " + cap + " and " + LobbyState.get().slotsUsed() + " are used."), false);
		return 1;
	}

	private static int setCap(ServerCommandSource source, int slots) {
		LobbyState.get().setCap(slots);
		source.sendFeedback(() -> Messages.success(slots == 0
				? "The cap is off - everybody gets let straight through."
				: "The cap is " + slots + "."), true);
		return 1;
	}

	/**
	 * Ends the session: the line is closed and cleared, every slot is given back and everybody who
	 * is not staff goes to the lobby.
	 */
	private static int end(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		LobbyState state = LobbyState.get();
		state.setQueueOpen(false);

		int moved = LobbyManager.recallEveryone(server);
		state.clearAdmitted();
		int cleared = state.clearQueue();

		source.sendFeedback(() -> Messages.success("Session over: " + moved + " sent to the lobby, "
				+ cleared + " taken out of the line, and the queue is closed."), true);
		return moved;
	}

	private static int bypass(ServerCommandSource source, Collection<ServerPlayerEntity> targets) {
		MinecraftServer server = source.getServer();
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			LobbyManager.admit(server, target, true);
			count++;
		}

		int total = count;
		source.sendFeedback(() -> Messages.success("Let " + total + " player(s) straight through."), true);
		return count;
	}

	private static int earlyAdd(ServerCommandSource source, Collection<ServerPlayerEntity> targets) {
		MinecraftServer server = source.getServer();
		LobbyState state = LobbyState.get();
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			if (state.addEarlyAccess(target.getUuid(), target.getGameProfile().name())) {
				count++;
			}

			if (LobbyManager.isMember(target)) {
				LobbyManager.admit(server, target, true);
			}
		}

		int total = count;
		source.sendFeedback(() -> Messages.success("Added " + total + " player(s) to early access."), true);
		return count;
	}

	private static int earlyRemove(ServerCommandSource source, String name) {
		LobbyState state = LobbyState.get();
		UUID uuid = state.earlyAccessByName(name);

		if (uuid == null || !state.removeEarlyAccess(uuid)) {
			source.sendError(Messages.failure(name + " is not on the early access list."));
			return 0;
		}

		source.sendFeedback(() -> Messages.success(name + " no longer has early access."), true);
		return 1;
	}

	private static int earlyList(ServerCommandSource source) {
		Map<UUID, String> early = LobbyState.get().earlyAccess();

		if (early.isEmpty()) {
			source.sendFeedback(() -> Messages.success("Nobody is on the early access list."), false);
			return 0;
		}

		source.sendFeedback(() -> Messages.header("Early access (" + early.size() + ")"), false);

		for (String name : early.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
			source.sendFeedback(() -> Messages.listEntry("  " + name), false);
		}

		return early.size();
	}
}
