package dev.mtpl.freezemute.lobby;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.mtpl.freezemute.FreezeMuteConfig;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The parkour: timers, checkpoints and the void catch.
 *
 * <p>Runs are held in memory only. A course and its times are worth keeping across a restart, a
 * half finished attempt is not.
 *
 * <p>Standing on the start pad keeps resetting the clock, so the timer really begins the moment a
 * runner steps off it. Checkpoints have to be taken in order, and the last one reached is where a
 * fall puts you back, which is the only thing that stops the void under a course from being a
 * punishment.
 */
public final class Parkour {
	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();

	/** An attempt in progress. */
	public record Run(String course, long startedAt, int checkpointsTaken) {
		public Run withCheckpoint() {
			return new Run(course, startedAt, checkpointsTaken + 1);
		}

		public long elapsed(long now) {
			return Math.max(0L, now - startedAt);
		}
	}

	private Parkour() {
	}

	public static Run runOf(UUID uuid) {
		return RUNS.get(uuid);
	}

	public static void forget(UUID uuid) {
		RUNS.remove(uuid);
	}

	public static void forgetEveryone() {
		RUNS.clear();
	}

	/**
	 * One member, one tick. Catches a fall first, then works out where on a course they are.
	 *
	 * @param display whether this tick is one of the ones that refreshes the action bar; the timer
	 *                only needs redrawing a few times a second, not twenty
	 */
	public static void tick(MinecraftServer server, ServerPlayerEntity player, long now, boolean display) {
		LobbyState state = LobbyState.get();
		FreezeMuteConfig config = FreezeMuteConfig.get();
		UUID uuid = player.getUuid();

		if (player.getY() < catchLevel(state, config)) {
			catchFall(server, player, state);
			return;
		}

		Collection<Course> courses = state.courseValues();

		if (courses.isEmpty()) {
			return;
		}

		double radius = Math.max(0.5D, config.lobbyCheckpointRadius);
		double radiusSquared = radius * radius;
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();

		for (Course course : courses) {
			if (course.start().distanceSquared(x, y, z) <= radiusSquared) {
				// On the pad: the clock stays at zero until they step off it.
				RUNS.put(uuid, new Run(course.name(), now, 0));

				if (display) {
					show(player, Text.literal(course.name() + " - ready").formatted(Formatting.GRAY));
				}

				return;
			}
		}

		Run run = RUNS.get(uuid);

		if (run == null) {
			return;
		}

		Course course = state.course(run.course());

		if (course == null) {
			RUNS.remove(uuid);
			return;
		}

		List<Spot> checkpoints = course.checkpoints();

		if (run.checkpointsTaken() < checkpoints.size()
				&& checkpoints.get(run.checkpointsTaken()).distanceSquared(x, y, z) <= radiusSquared) {
			Run advanced = run.withCheckpoint();
			RUNS.put(uuid, advanced);
			player.sendMessage(Text.literal("Checkpoint " + advanced.checkpointsTaken() + " of "
					+ checkpoints.size() + " - " + CourseRecord.format(advanced.elapsed(now)))
					.formatted(Formatting.AQUA));
			LobbyManager.playCue(player);
			run = advanced;
		}

		if (course.playable()
				&& run.checkpointsTaken() >= checkpoints.size()
				&& course.finish().distanceSquared(x, y, z) <= radiusSquared) {
			finish(server, player, course, run, now);
			return;
		}

		if (display) {
			show(player, Text.literal(course.name() + "  " + CourseRecord.format(run.elapsed(now))
					+ "  [" + run.checkpointsTaken() + "/" + checkpoints.size() + "]").formatted(Formatting.YELLOW));
		}
	}

	private static void finish(MinecraftServer server, ServerPlayerEntity player, Course course, Run run, long now) {
		UUID uuid = player.getUuid();
		RUNS.remove(uuid);

		long millis = run.elapsed(now);
		LobbyState state = LobbyState.get();
		CourseRecord previous = state.personalBest(course.name(), uuid);
		boolean best = state.recordTime(course.name(),
				new CourseRecord(uuid, player.getGameProfile().name(), millis, now));

		List<CourseRecord> board = state.leaderboard(course.name());
		int place = 0;

		for (int index = 0; index < board.size(); index++) {
			if (board.get(index).uuid().equals(uuid)) {
				place = index + 1;
				break;
			}
		}

		LobbyManager.title(player,
				Text.literal(CourseRecord.format(millis)).formatted(best ? Formatting.GREEN : Formatting.WHITE),
				Text.literal(best ? "Personal best" : "Your best is still "
						+ CourseRecord.format(previous == null ? millis : previous.millis())).formatted(Formatting.GRAY));
		LobbyManager.playCue(player);

		StringBuilder message = new StringBuilder(course.name()).append(" finished in ")
				.append(CourseRecord.format(millis));

		if (best) {
			message.append(" - a personal best");
		}

		if (place > 0) {
			message.append(place == 1 ? ", and the fastest on the board" : ", place " + place + " on the board");
		}

		player.sendMessage(Text.literal(message.toString()).formatted(Formatting.GREEN));
	}

	/**
	 * How far somebody has to fall before they are caught.
	 *
	 * <p>The configured height is an absolute one, which is right for a lobby built near it and
	 * a very long drop for one built high up - an island with its water at y 53 would make a
	 * missed jump a four second fall to y -5. So the catch also tracks the spawn: whichever of
	 * the two is higher wins, and a fall is over about twenty blocks after it starts.
	 */
	private static double catchLevel(LobbyState state, FreezeMuteConfig config) {
		return Math.max(config.lobbyVoidCatchY, state.spawn().y() - 24.0D);
	}

	/** Puts a runner back on their last checkpoint, or on the lobby spawn if they were not running. */
	private static void catchFall(MinecraftServer server, ServerPlayerEntity player, LobbyState state) {
		Run run = RUNS.get(player.getUuid());
		Spot target = state.spawn();

		if (run != null) {
			Course course = state.course(run.course());

			if (course != null) {
				target = course.respawnFor(run.checkpointsTaken());
			}
		}

		// The caller only reaches here for somebody standing in the lobby, so that is the world.
		ServerWorld world = LobbyDimension.world(server);

		if (world != null) {
			player.setVelocity(0.0D, 0.0D, 0.0D);
			player.teleport(world, target.x(), target.y(), target.z(), Set.<PositionFlag>of(),
					target.yaw(), target.pitch(), true);
		}

		if (run != null) {
			show(player, Text.literal("Back to the last checkpoint").formatted(Formatting.GRAY));
		}
	}

	private static void show(ServerPlayerEntity player, Text text) {
		LobbyManager.actionBar(player, text);
	}
}
