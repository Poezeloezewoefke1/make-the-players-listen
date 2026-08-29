package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.List;

import dev.mtpl.freezemute.FreezeMute;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Builds the whole room: a floor to stand on, a wall so nobody wanders off, a pedestal to put an
 * NPC on, and a parkour course that is registered as it is laid, so the timers and the leaderboard
 * work the moment the last block is placed.
 *
 * <p>Everything is generated rather than shipped as a structure file. A structure would have to be
 * loaded through machinery this mod deliberately does not depend on, and a lobby is simple enough
 * geometry that describing it in code is both shorter and easier to adjust.
 *
 * <p>The jumps are deliberately gentle. This is the room people wait in, not a course anybody
 * chose: three blocks apart, and a rise of one only on every third jump, which is comfortably
 * inside what a sprint jump does. Somebody who wants a hard course can build one.
 */
public final class LobbyBuilder {
	/** Half-width of the floor, so the room is this many blocks across twice over, plus one. */
	private static final int RADIUS = 20;
	private static final int WALL_HEIGHT = 4;
	/** A lantern every this many blocks along the top of the wall. */
	private static final int LANTERN_SPACING = 5;

	private static final int COURSE_STEPS = 24;
	private static final double COURSE_RING = 14.0D;
	private static final double COURSE_GAP = 3.0D;
	/** One block up every third jump. */
	private static final int COURSE_RISE_EVERY = 3;
	public static final String COURSE_NAME = "spawn";

	private LobbyBuilder() {
	}

	/** What a build did, so the command can say something useful. */
	public record Result(int blocks, Spot spawn, Spot queuePoint, Course course) {
	}

	/**
	 * Lays the room out around a middle point. The floor sits one block below {@code centre}, so
	 * {@code centre} is where feet end up.
	 */
	public static Result build(ServerWorld world, Spot centre) {
		int originX = (int) Math.floor(centre.x());
		int originZ = (int) Math.floor(centre.z());
		int floorY = (int) Math.floor(centre.y()) - 1;
		int blocks = 0;

		blocks += floor(world, originX, floorY, originZ);
		blocks += wall(world, originX, floorY, originZ);
		blocks += clearHeadroom(world, originX, floorY, originZ);

		BlockPos pedestal = new BlockPos(originX, floorY + 1, originZ - RADIUS + 4);
		blocks += pedestal(world, pedestal);

		LaidCourse laid = new CourseBuild(world, originX, floorY, originZ).lay();
		blocks += laid.blocks();

		Spot spawn = new Spot(originX + 0.5D, floorY + 1.0D, originZ + 0.5D, 180.0F, 0.0F);
		Spot queuePoint = new Spot(pedestal.getX() + 0.5D, pedestal.getY() + 1.0D, pedestal.getZ() + 0.5D,
				0.0F, 0.0F);

		LobbyState state = LobbyState.get();
		state.setSpawn(spawn);
		state.setQueuePoint(queuePoint);
		state.putCourse(laid.course());

		FreezeMute.LOGGER.info("Lobby: built the room at {} {} {} - {} blocks, spawn {}, queue point {}",
				originX, floorY, originZ, blocks, spawn.describe(), queuePoint.describe());

		return new Result(blocks, spawn, queuePoint, laid.course());
	}

	/** A plain floor with a border ring, so the edge reads as an edge. */
	private static int floor(ServerWorld world, int originX, int floorY, int originZ) {
		int placed = 0;

		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				boolean border = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS
						|| Math.abs(x) == RADIUS - 1 || Math.abs(z) == RADIUS - 1;
				Block block = border ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE;
				world.setBlockState(new BlockPos(originX + x, floorY, originZ + z), block.getDefaultState());
				placed++;
			}
		}

		return placed;
	}

	private static int wall(ServerWorld world, int originX, int floorY, int originZ) {
		int placed = 0;

		for (int offset = -RADIUS; offset <= RADIUS; offset++) {
			for (int height = 1; height <= WALL_HEIGHT; height++) {
				boolean top = height == WALL_HEIGHT;
				boolean lantern = top && Math.floorMod(offset + RADIUS, LANTERN_SPACING) == 0;
				Block block = lantern ? Blocks.SEA_LANTERN : Blocks.SMOOTH_STONE;

				placed += place(world, originX + offset, floorY + height, originZ - RADIUS, block);
				placed += place(world, originX + offset, floorY + height, originZ + RADIUS, block);
				placed += place(world, originX - RADIUS, floorY + height, originZ + offset, block);
				placed += place(world, originX + RADIUS, floorY + height, originZ + offset, block);
			}
		}

		return placed;
	}

	/**
	 * Empties the space above the floor. The dimension generates as void, so normally there is
	 * nothing here - but a room built over an older one has to start from an empty room.
	 */
	private static int clearHeadroom(ServerWorld world, int originX, int floorY, int originZ) {
		int placed = 0;

		for (int x = -RADIUS + 1; x <= RADIUS - 1; x++) {
			for (int z = -RADIUS + 1; z <= RADIUS - 1; z++) {
				for (int height = 1; height <= WALL_HEIGHT; height++) {
					BlockPos pos = new BlockPos(originX + x, floorY + height, originZ + z);

					if (!world.getBlockState(pos).isAir()) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState());
						placed++;
					}
				}
			}
		}

		return placed;
	}

	/**
	 * Where the NPC goes. Nothing is spawned on it: whatever people use for NPCs is their own
	 * business, and a right click anywhere near this spot joins the queue whether there is
	 * something standing here or not.
	 */
	private static int pedestal(ServerWorld world, BlockPos middle) {
		int placed = 0;

		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				boolean corner = x != 0 && z != 0;
				placed += place(world, middle.getX() + x, middle.getY(), middle.getZ() + z,
						corner ? Blocks.POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS);
			}
		}

		placed += place(world, middle.getX() - 2, middle.getY() + 1, middle.getZ(), Blocks.SEA_LANTERN);
		placed += place(world, middle.getX() + 2, middle.getY() + 1, middle.getZ(), Blocks.SEA_LANTERN);
		return placed;
	}

	private static int place(ServerWorld world, int x, int y, int z, Block block) {
		world.setBlockState(new BlockPos(x, y, z), block.getDefaultState());
		return 1;
	}

	/** The parkour: a rising spiral of single blocks, with the course registered as it is laid. */
	private record LaidCourse(Course course, int blocks) {
	}

	private static final class CourseBuild {
		private final ServerWorld world;
		private final int originX;
		private final int floorY;
		private final int originZ;

		CourseBuild(ServerWorld world, int originX, int floorY, int originZ) {
			this.world = world;
			this.originX = originX;
			this.floorY = floorY;
			this.originZ = originZ;
		}

		LaidCourse lay() {
			// The angle between two steps that puts them COURSE_GAP apart on a ring this wide.
			double step = 2.0D * Math.asin(Math.min(1.0D, COURSE_GAP / (2.0D * COURSE_RING)));
			double angle = Math.PI;
			int blocks = 0;
			int y = floorY + 2;

			List<Spot> pads = new ArrayList<>();

			for (int index = 0; index <= COURSE_STEPS; index++) {
				int x = (int) Math.round(originX + COURSE_RING * Math.cos(angle));
				int z = (int) Math.round(originZ + COURSE_RING * Math.sin(angle));

				boolean start = index == 0;
				boolean finish = index == COURSE_STEPS;
				boolean checkpoint = !start && !finish && index % 8 == 0;

				Block block = Blocks.QUARTZ_BLOCK;

				if (start) {
					block = Blocks.GOLD_BLOCK;
				} else if (finish) {
					block = Blocks.DIAMOND_BLOCK;
				} else if (checkpoint) {
					block = Blocks.EMERALD_BLOCK;
				}

				world.setBlockState(new BlockPos(x, y, z), block.getDefaultState());
				blocks++;

				if (start || finish || checkpoint) {
					pads.add(new Spot(x + 0.5D, y + 1.0D, z + 0.5D, 0.0F, 0.0F));
				}

				angle += step;

				if (index % COURSE_RISE_EVERY == COURSE_RISE_EVERY - 1) {
					y++;
				}
			}

			Course course = Course.starting(COURSE_NAME, pads.get(0));

			for (int index = 1; index < pads.size() - 1; index++) {
				course = course.withCheckpoint(pads.get(index));
			}

			course = course.withFinish(pads.get(pads.size() - 1));
			return new LaidCourse(course, blocks);
		}
	}
}
