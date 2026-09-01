package dev.mtpl.freezemute.command;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FreezeMuteConfig;
import dev.mtpl.freezemute.lobby.Course;
import dev.mtpl.freezemute.lobby.LobbyBuilder;
import dev.mtpl.freezemute.lobby.CourseRecord;
import dev.mtpl.freezemute.lobby.LobbyDimension;
import dev.mtpl.freezemute.lobby.LobbyManager;
import dev.mtpl.freezemute.lobby.LobbyState;
import dev.mtpl.freezemute.lobby.Parkour;
import dev.mtpl.freezemute.lobby.PlayerWorld;
import dev.mtpl.freezemute.lobby.Spot;
import dev.mtpl.freezemute.util.Messages;

import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * {@code /lobby} - the room itself, and the parkour in it.
 *
 * <p>{@code /lobby all} is the panic button: everybody who is not staff comes back, immediately,
 * and the line they were in is kept so the session can pick up where it stopped.
 */
public final class LobbyCommand {
	private LobbyCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("lobby")
				.requires(source -> Permissions.check(source, Permissions.LOBBY))
				.executes(context -> sendSelf(context.getSource()))
				.then(CommandManager.literal("status")
						.executes(context -> status(context.getSource())))
				.then(CommandManager.literal("enable")
						.executes(context -> setEnabled(context.getSource(), true)))
				.then(CommandManager.literal("disable")
						.executes(context -> setEnabled(context.getSource(), false)))
				.then(CommandManager.literal("leave")
						.executes(context -> leave(context.getSource())))
				.then(CommandManager.literal("all")
						.executes(context -> recall(context.getSource())))
				.then(CommandManager.literal("setspawn")
						.executes(context -> setSpawn(context.getSource())))
				.then(CommandManager.literal("generate")
						.executes(context -> explainGenerate(context.getSource()))
						.then(CommandManager.literal("confirm")
								.executes(context -> generate(context.getSource()))))
				.then(CommandManager.literal("queuepoint")
						.executes(context -> setQueuePoint(context.getSource()))
						.then(CommandManager.literal("clear")
								.executes(context -> clearQueuePoint(context.getSource()))))
				.then(course())
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(context -> send(context.getSource(),
								EntityArgumentType.getPlayers(context, "targets")))));
	}

	// ------------------------------------------------------------------- rooms

	private static int sendSelf(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			return status(source);
		}

		LobbyManager.sendToLobby(source.getServer(), player);
		source.sendFeedback(() -> Messages.success("Off you go."), false);
		return 1;
	}

	private static int send(ServerCommandSource source, Collection<ServerPlayerEntity> targets) {
		MinecraftServer server = source.getServer();
		LobbyState state = LobbyState.get();
		long now = System.currentTimeMillis();
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			state.release(target.getUuid());
			LobbyManager.sendToLobby(server, target);

			// Queued the same way arriving in the lobby queues somebody: automatically when there
			// is nowhere to ask, and not at all when there is a pedestal to walk up to.
			if (!Permissions.isStaff(target) && !state.joinedAtAPoint()) {
				state.enqueue(target.getUuid(), target.getGameProfile().name(), now);
			}

			count++;
		}

		int total = count;
		source.sendFeedback(() -> Messages.success("Sent " + total + " player(s) to the lobby."), true);
		return count;
	}

	/**
	 * The way back out for staff who went in to look at the room.
	 *
	 * <p>It puts them where they were standing when they ran {@code /lobby}, in the game mode they
	 * were in, and touches neither the queue nor the slots - staff never held either.
	 */
	private static int leave(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Messages.failure("Only a player can leave the lobby."));
			return 0;
		}

		if (!LobbyManager.isInLobby(player)) {
			source.sendError(Messages.failure("You are not in the lobby."));
			return 0;
		}

		LobbyManager.sendToWorld(source.getServer(), player,
				Messages.success("Back to where you were."));
		return 1;
	}

	/** The panic button: everybody back, the line kept, nothing let through until staff say so. */
	private static int recall(ServerCommandSource source) {
		LobbyState.get().setQueueOpen(false);
		int moved = LobbyManager.recallEveryone(source.getServer());
		source.sendFeedback(() -> Messages.success(moved + " player(s) pulled back to the lobby. "
				+ "The queue is closed - run /queue open when you are ready."), true);
		return moved;
	}

	private static int setSpawn(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Messages.failure("Stand where you want the spawn to be and run this again."));
			return 0;
		}

		if (!LobbyManager.isInLobby(player)) {
			source.sendError(Messages.failure("You are not in the lobby. Run /lobby first."));
			return 0;
		}

		Spot spot = Spot.of(player);
		ServerWorld lobby = LobbyDimension.world(server);

		if (lobby != null && lobby.getBlockState(new BlockPos((int) Math.floor(spot.x()),
				(int) Math.floor(spot.y()) - 1, (int) Math.floor(spot.z()))).isAir()) {
			// A spawn over the void is not merely a bad spawn. Everybody arriving there falls, the
			// void catch puts them back on it, and they fall again - about once a second, for as
			// long as they stay. Standing on a block first is the whole fix, so it is worth asking.
			source.sendError(Messages.failure("There is nothing under that spot. A spawn over the void drops "
					+ "everybody who arrives straight back into it. Stand on a block and run this again."));
			return 0;
		}

		LobbyState.get().setSpawn(spot);
		source.sendFeedback(() -> Messages.success("Lobby spawn set to " + spot.describe() + "."), true);
		return 1;
	}

	/**
	 * {@code /lobby generate} on its own only says what it would do. Laying a room over whatever
	 * somebody spent an evening building, because they typed one word, would be unforgivable.
	 */
	private static int explainGenerate(ServerCommandSource source) {
		source.sendFeedback(() -> Messages.header("This will build an island"), false);
		source.sendFeedback(() -> Messages.listEntry("  five terraces of ground with banded cliffs between "
				+ "them, a beach, and a lagoon all the way round"), false);
		source.sendFeedback(() -> Messages.listEntry("  a town on the terrace: a great hall, a watchtower, "
				+ "a market with striped canopies, and hedged gardens, with stepped paths up to the plaza"), false);
		source.sendFeedback(() -> Messages.listEntry("  a plaza on top with a fountain, a ring of lamps, "
				+ "and a pedestal with an NPC standing on it"), false);
		source.sendFeedback(() -> Messages.listEntry("  palms, rock outcrops, a jetty, a lighthouse, a "
				+ "shelter, hot air balloons overhead, and a parkour course spiralling up off the plaza"), false);
		source.sendFeedback(() -> Messages.listEntry("  it replaces everything within about "
				+ LobbyBuilder.reach() + " blocks of you, and moves the lobby spawn and the queue point"), false);
		source.sendFeedback(() -> Messages.listEntry("  the water sits " + LobbyBuilder.summit()
				+ " blocks below where you stand, so stand where you want the top of the island"), false);
		source.sendFeedback(() -> Messages.listEntry("  it is a few hundred thousand blocks and goes down "
				+ "over a few seconds, a slice per tick, so the server keeps running while it does"), false);
		source.sendFeedback(() -> Messages.failure("Run /lobby generate confirm if that is what you want."), false);
		return 1;
	}

	private static int generate(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		MinecraftServer server = source.getServer();
		ServerWorld lobby = LobbyDimension.world(server);

		if (lobby == null) {
			source.sendError(Messages.failure("The lobby dimension does not exist yet. Restart the server once."));
			return 0;
		}

		if (player != null && !LobbyManager.isInLobby(player)) {
			source.sendError(Messages.failure("Stand in the lobby first - run /lobby, then come back to this."));
			return 0;
		}

		if (LobbyBuilder.building()) {
			source.sendError(Messages.failure("An island is still going down (" + LobbyBuilder.progress()
					+ "%). Wait for it to finish before starting another."));
			return 0;
		}

		Spot centre = player == null ? LobbyState.get().spawn() : Spot.of(player);
		UUID commander = player == null ? null : player.getUuid();

		// Everything that moves people runs when the last block is down, not now. Now there is
		// nothing under the new spawn to put them on.
		LobbyBuilder.Result result = LobbyBuilder.build(lobby, centre,
				() -> landEverybody(server, lobby, commander));

		source.sendFeedback(() -> Messages.success("Laying the island: " + result.blocks()
				+ " blocks, spawn at " + result.spawn().describe() + "."), true);
		source.sendFeedback(() -> Messages.listEntry("  it goes down over the next few seconds rather than "
				+ "all at once, so the server keeps running while it does"), false);
		source.sendFeedback(() -> Messages.listEntry("  the queue point is the black pedestal at "
				+ result.queuePoint().describe() + " - stand an NPC on it if you like, or leave it bare"), false);
		source.sendFeedback(() -> Messages.listEntry("  players right click there to join the queue; "
				+ "arriving in the lobby no longer queues them on its own"), false);
		source.sendFeedback(() -> Messages.listEntry("  the parkour course '" + result.course().name() + "' has "
				+ result.course().checkpoints().size() + " checkpoints - /lobby course top "
				+ result.course().name() + " for the times"), false);

		return result.blocks();
	}

	/**
	 * Puts everybody who was watching it go up on top of it, once it is up.
	 *
	 * <p>Members had the ground replaced under them and the spawn moved out from under them.
	 * Whoever ran the command is moved too: it asks them to stand where they want the top of the
	 * island, and the top of the island is a solid block, so leaving them there leaves them inside
	 * it. They are staff, so the member loop does not reach them.
	 */
	private static void landEverybody(MinecraftServer server, ServerWorld lobby, UUID commander) {
		int moved = 0;

		for (ServerPlayerEntity waiting : server.getPlayerManager().getPlayerList()) {
			if (LobbyManager.isMember(waiting)) {
				LobbyManager.sendToLobby(server, waiting);
				moved++;
			}
		}

		if (commander != null) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(commander);

			if (player != null && !LobbyManager.isMember(player) && LobbyManager.isInLobby(player)) {
				Spot landing = LobbyState.get().spawn();
				player.teleport(lobby, landing.x(), landing.y(), landing.z(), Set.<PositionFlag>of(),
						landing.yaw(), landing.pitch(), true);
				player.sendMessage(Messages.success("The island is finished."));
			}
		}

		if (moved > 0) {
			FreezeMute.LOGGER.info("Lobby: put {} player(s) on the new spawn", moved);
		}
	}

	private static int setQueuePoint(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Messages.failure("Stand where the queue point should be and run this again."));
			return 0;
		}

		if (!LobbyManager.isInLobby(player)) {
			source.sendError(Messages.failure("The queue point belongs in the lobby. Run /lobby first."));
			return 0;
		}

		Spot spot = Spot.of(player);
		LobbyState.get().setQueuePoint(spot);
		source.sendFeedback(() -> Messages.success("Queue point set at " + spot.describe()
				+ ". Players right click within "
				+ (int) FreezeMuteConfig.get().lobbyQueuePointRadius + " blocks of it to join the queue, "
				+ "and arriving in the lobby no longer queues them on its own."), true);
		return 1;
	}

	private static int clearQueuePoint(ServerCommandSource source) {
		LobbyState.get().setQueuePoint(null);
		source.sendFeedback(() -> Messages.success("Queue point cleared. Everybody who arrives in the lobby "
				+ "is put in the queue automatically again."), true);
		return 1;
	}

	private static int setEnabled(ServerCommandSource source, boolean enabled) {
		MinecraftServer server = source.getServer();

		if (enabled && LobbyDimension.world(server) == null) {
			source.sendError(Messages.failure("The lobby dimension does not exist yet. The data pack has been "
					+ "written - restart the server once and it will be there."));
			return 0;
		}

		if (enabled && !PlayerWorld.available()) {
			// Switching it on anyway would fill the queue with people the lobby could then never
			// let out again, because knowing who is standing in the room is what lets it move
			// anybody back. The startup log says which lookup is missing.
			source.sendError(Messages.failure("This Minecraft build names the player world lookup something "
					+ "the mod does not know, so the lobby cannot tell who is in it and would trap anybody "
					+ "it let in. See the server log from startup. Freezing, muting and kits still work."));
			return 0;
		}

		LobbyState state = LobbyState.get();
		state.setEnabled(enabled);

		if (!enabled) {
			// Leaving people stuck in an adventure-mode room nobody is watching would be worse
			// than the queue jumping, so everybody comes out - and they are let go rather than
			// recorded as admitted, or turning the lobby back on would wave them all through.
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (LobbyManager.isMember(player)) {
					state.dequeue(player.getUuid());
					state.release(player.getUuid());
					LobbyManager.sendToWorld(server, player, null);
				}
			}

			source.sendFeedback(() -> Messages.success("The lobby is off and everybody has been let out."), true);
			return 1;
		}

		// Everybody already on the server is sorted out now rather than the next time they
		// happen to reconnect; a cap that only applies to future arrivals is not a cap.
		int held = 0;
		long now = System.currentTimeMillis();

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (Permissions.isStaff(player) || Permissions.hasEarlyAccess(player)
					|| state.hasEarlyAccess(player.getUuid())) {
				continue;
			}

			if (LobbyManager.hasFreeSlot(state)) {
				state.admit(player.getUuid(), player.getGameProfile().name(), now);
				continue;
			}

			state.enqueue(player.getUuid(), player.getGameProfile().name(), now);
			LobbyManager.sendToLobby(server, player);
			held++;
		}

		int moved = held;
		source.sendFeedback(() -> Messages.success("The lobby is on. Everybody who joins now waits in line"
				+ (moved == 0 ? "." : ", and " + moved + " already on the server went to it.")), true);
		return 1;
	}

	private static int status(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		LobbyState state = LobbyState.get();
		ServerWorld world = LobbyDimension.world(server);

		source.sendFeedback(() -> Messages.header("Lobby"), false);
		source.sendFeedback(() -> Messages.listEntry("  dimension: "
				+ (world == null ? "not built yet - restart once" : "astra:lobby")), false);
		source.sendFeedback(() -> Messages.listEntry("  routing: " + (state.enabled() ? "on" : "off")
				+ (PlayerWorld.available() ? "" : " - but the player world lookup is missing on this build, "
						+ "so nobody is being routed at all")), false);
		source.sendFeedback(() -> Messages.listEntry("  spawn: " + state.spawn().describe()), false);
		source.sendFeedback(() -> Messages.listEntry("  joining the queue: " + (state.joinedAtAPoint()
				? "right click the queue point at " + state.queuePoint().describe()
				: "automatic on arrival, there is no queue point")), false);
		source.sendFeedback(() -> Messages.listEntry("  waiting here: " + LobbyManager.memberCount()), false);
		source.sendFeedback(() -> Messages.listEntry("  courses: " + state.courses().size()), false);
		return 1;
	}

	// ----------------------------------------------------------------- parkour

	private static LiteralArgumentBuilder<ServerCommandSource> course() {
		return CommandManager.literal("course")
				.requires(source -> Permissions.check(source, Permissions.LOBBY_COURSE))
				.then(CommandManager.literal("list")
						.executes(context -> courseList(context.getSource())))
				.then(CommandManager.literal("create")
						.then(CommandManager.argument("name", StringArgumentType.word())
								.executes(context -> create(context.getSource(),
										StringArgumentType.getString(context, "name")))))
				.then(named("start", LobbyCommand::setStart))
				.then(named("checkpoint", LobbyCommand::addCheckpoint))
				.then(named("undo", LobbyCommand::undoCheckpoint))
				.then(named("finish", LobbyCommand::setFinish))
				.then(named("delete", LobbyCommand::delete))
				.then(named("tp", LobbyCommand::teleport))
				.then(named("top", LobbyCommand::top));
	}

	/** Every course sub-command but {@code create} takes the name of a course that already exists. */
	private static LiteralArgumentBuilder<ServerCommandSource> named(String literal, CourseAction action) {
		RequiredArgumentBuilder<ServerCommandSource, String> argument =
				CommandManager.argument("name", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(
								LobbyState.get().courseNames(), builder))
						.executes(context -> action.run(context.getSource(),
								StringArgumentType.getString(context, "name")));

		return CommandManager.literal(literal).then(argument);
	}

	@FunctionalInterface
	private interface CourseAction {
		int run(ServerCommandSource source, String name);
	}

	private static int courseList(ServerCommandSource source) {
		List<Course> courses = LobbyState.get().courses();

		if (courses.isEmpty()) {
			source.sendFeedback(() -> Messages.success("No courses yet. Stand on the start and run "
					+ "/lobby course create <name>."), false);
			return 0;
		}

		source.sendFeedback(() -> Messages.header("Courses (" + courses.size() + ")"), false);

		for (Course course : courses) {
			source.sendFeedback(() -> Messages.listEntry("  " + course.name() + " - "
					+ course.checkpoints().size() + " checkpoint(s), "
					+ (course.playable() ? "ready" : "no finish set yet")), false);
		}

		return courses.size();
	}

	private static int create(ServerCommandSource source, String name) {
		Spot spot = standingIn(source);

		if (spot == null) {
			return 0;
		}

		LobbyState state = LobbyState.get();

		if (state.course(name) != null) {
			source.sendError(Messages.failure("There is already a course called " + name + "."));
			return 0;
		}

		state.putCourse(Course.starting(name, spot));
		source.sendFeedback(() -> Messages.success("Created " + name + " with the start where you stand. "
				+ "Add checkpoints with /lobby course checkpoint " + name
				+ " and set the end with /lobby course finish " + name + "."), true);
		return 1;
	}

	private static int setStart(ServerCommandSource source, String name) {
		Spot spot = standingIn(source);
		Course course = existing(source, name);

		if (spot == null || course == null) {
			return 0;
		}

		// The same course nobody can finish, made from the other end. Moving the start onto the
		// finish - or onto a checkpoint - is exactly what setting a finish on the start is
		// refused for, and refusing only one spelling of it refuses nothing.
		double radius = FreezeMuteConfig.get().lobbyCheckpointRadius;
		Course moved = course.withStart(spot);

		if (Parkour.swallowedByTheStart(spot, course.finish(), radius)) {
			source.sendError(tooCloseToTheStart(course.name(), "the finish of"));
			return 0;
		}

		for (Spot checkpoint : course.checkpoints()) {
			if (Parkour.swallowedByTheStart(spot, checkpoint, radius)) {
				source.sendError(tooCloseToTheStart(course.name(), "a checkpoint of"));
				return 0;
			}
		}

		LobbyState.get().putCourse(moved);
		source.sendFeedback(() -> Messages.success(moved.name() + " start moved to " + spot.describe() + "."), true);
		return 1;
	}

	/** One message, because it is one mistake however it was arrived at. */
	private static net.minecraft.text.Text tooCloseToTheStart(String course, String what) {
		int blocks = (int) Math.ceil(Parkour.startRadius(FreezeMuteConfig.get().lobbyCheckpointRadius));
		return Messages.failure("That would put " + what + " " + course + " on top of its start. Standing on "
				+ "the start begins a run, and that is checked before anything else, so nothing within "
				+ blocks + " blocks of it can ever be reached.");
	}

	private static int setFinish(ServerCommandSource source, String name) {
		Spot spot = standingIn(source);
		Course course = existing(source, name);

		if (spot == null || course == null) {
			return 0;
		}

		if (Parkour.swallowedByTheStart(course.start(), spot, FreezeMuteConfig.get().lobbyCheckpointRadius)) {
			source.sendError(tooCloseToTheStart(course.name(), "a finish"));
			return 0;
		}

		Course updated = course.withFinish(spot);
		LobbyState.get().putCourse(updated);
		source.sendFeedback(() -> Messages.success(updated.name() + " finish set at " + spot.describe() + "."), true);
		return 1;
	}

	private static int addCheckpoint(ServerCommandSource source, String name) {
		Spot spot = standingIn(source);
		Course course = existing(source, name);

		if (spot == null || course == null) {
			return 0;
		}

		if (Parkour.swallowedByTheStart(course.start(), spot, FreezeMuteConfig.get().lobbyCheckpointRadius)) {
			source.sendError(tooCloseToTheStart(course.name(), "a checkpoint of"));
			return 0;
		}

		Course updated = course.withCheckpoint(spot);
		LobbyState.get().putCourse(updated);
		source.sendFeedback(() -> Messages.success("Checkpoint " + updated.checkpoints().size()
				+ " added to " + updated.name() + " at " + spot.describe() + "."), true);
		return updated.checkpoints().size();
	}

	private static int undoCheckpoint(ServerCommandSource source, String name) {
		Course course = existing(source, name);

		if (course == null) {
			return 0;
		}

		if (course.checkpoints().isEmpty()) {
			source.sendError(Messages.failure(course.name() + " has no checkpoints to remove."));
			return 0;
		}

		Course updated = course.withoutLastCheckpoint();
		LobbyState.get().putCourse(updated);
		source.sendFeedback(() -> Messages.success("Removed the last checkpoint from " + updated.name()
				+ ", " + updated.checkpoints().size() + " left."), true);
		return 1;
	}

	private static int delete(ServerCommandSource source, String name) {
		if (!LobbyState.get().removeCourse(name)) {
			source.sendError(Messages.failure("There is no course called " + name + "."));
			return 0;
		}

		source.sendFeedback(() -> Messages.success("Deleted " + name + " and its times."), true);
		return 1;
	}

	private static int teleport(ServerCommandSource source, String name) {
		ServerPlayerEntity player = source.getPlayer();
		Course course = existing(source, name);

		if (course == null) {
			return 0;
		}

		if (player == null) {
			source.sendError(Messages.failure("Only a player can be teleported to a course."));
			return 0;
		}

		ServerWorld lobby = LobbyDimension.world(source.getServer());

		if (lobby == null) {
			source.sendError(Messages.failure("The lobby dimension does not exist yet."));
			return 0;
		}

		Spot start = course.start();
		player.teleport(lobby, start.x(), start.y(), start.z(), Set.of(), start.yaw(), start.pitch(), true);
		source.sendFeedback(() -> Messages.success("Teleported to the start of " + course.name() + "."), false);
		return 1;
	}

	private static int top(ServerCommandSource source, String name) {
		Course course = existing(source, name);

		if (course == null) {
			return 0;
		}

		List<CourseRecord> board = LobbyState.get().leaderboard(course.name());

		if (board.isEmpty()) {
			source.sendFeedback(() -> Messages.success("Nobody has finished " + course.name() + " yet."), false);
			return 0;
		}

		source.sendFeedback(() -> Messages.header(course.name() + " - fastest times"), false);

		int shown = Math.min(10, board.size());

		for (int index = 0; index < shown; index++) {
			CourseRecord record = board.get(index);
			int place = index + 1;
			source.sendFeedback(() -> Messages.listEntry("  " + place + ". " + record.name()
					+ " - " + CourseRecord.format(record.millis())), false);
		}

		ServerPlayerEntity player = source.getPlayer();

		if (player != null) {
			CourseRecord own = LobbyState.get().personalBest(course.name(), player.getUuid());

			if (own != null) {
				int place = board.indexOf(own) + 1;
				source.sendFeedback(() -> Messages.success("Your best: " + CourseRecord.format(own.millis())
						+ ", place " + place + " of " + board.size()), false);
			}
		}

		return shown;
	}

	private static Course existing(ServerCommandSource source, String name) {
		Course course = LobbyState.get().course(name);

		if (course == null) {
			source.sendError(Messages.failure("There is no course called " + name + "."));
		}

		return course;
	}

	/** A course is built by standing where you want the marker, so every edit needs a player. */
	private static Spot standingIn(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Messages.failure("Stand where you want the marker and run this again."));
			return null;
		}

		if (!LobbyManager.isInLobby(player)) {
			source.sendError(Messages.failure("Courses are built in the lobby. Run /lobby first."));
			return null;
		}

		return Spot.of(player);
	}
}
