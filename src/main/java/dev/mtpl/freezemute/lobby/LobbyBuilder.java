package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dev.mtpl.freezemute.FreezeMute;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Builds the island the lobby sits on.
 *
 * <p>This is generated rather than shipped as a structure file, because loading one would mean
 * depending on machinery this mod deliberately does without. Generated also means it is not a
 * hand made showcase build and never will be - what it can be is a proper place rather than a
 * box: tiered ground with cliffs between the levels, a beach running into a lagoon, palms, a
 * jetty out over the water, a lighthouse, and balloons overhead.
 *
 * <p>Everything is laid from one seed derived from where it is built, so running it twice in the
 * same place gives the same island, and running it somewhere else gives a different one.
 */
public final class LobbyBuilder {
	/** Water surface, relative to the middle of the build. */
	private static final int SEA = 0;
	/** How far the shoreline reaches before the wobble is added. */
	private static final double COAST = 26.0D;
	/** The lagoon and the shoal that holds it in. */
	private static final int WATER_RADIUS = 36;
	/** Height of the flat top the plaza sits on, above the water. */
	private static final int SUMMIT = 12;
	private static final int PLAZA_RADIUS = 8;

	private static final int COURSE_STEPS = 24;
	private static final double COURSE_RING = 19.0D;
	private static final double COURSE_GAP = 3.0D;
	private static final int COURSE_RISE_EVERY = 3;
	public static final String COURSE_NAME = "spawn";

	private LobbyBuilder() {
	}

	/** What a build did, so the command can say something useful. */
	public record Result(int blocks, Spot spawn, Spot queuePoint, Course course) {
	}

	/** One block of the island, in world coordinates. Applied in order, so later ones win. */
	public record Placement(int x, int y, int z, Material material) {
	}

	/** Everything a build decided, before any of it touches a world. */
	public record Plan(List<Placement> placements, Spot spawn, Spot queuePoint, Course course) {
	}

	/**
	 * Works out the whole island without a world to put it in.
	 *
	 * <p>Being able to ask for the island as a list rather than as a side effect is what makes it
	 * checkable: whether the lagoon holds water, whether the plaza is stood on solid ground,
	 * whether the jetty is over the sea instead of buried in a hill, are all questions about this
	 * list, and none of them need Minecraft to answer.
	 *
	 * @param sea the height the water surface sits at
	 */
	public static Plan plan(int originX, int originZ, int sea) {
		Random random = new Random(originX * 341873128712L + originZ * 132897987541L);
		Site site = new Site(originX, originZ, sea, random);

		site.ground();
		site.plaza();
		site.palms();
		site.rocks();
		site.jetty();
		site.lighthouse();
		site.pavilion();
		site.balloons();
		site.water();

		int pedestalX = originX;
		int pedestalY = sea + SUMMIT + 1;
		int pedestalZ = originZ - 5;
		site.pedestal(pedestalX, pedestalY, pedestalZ);

		Course course = site.course();

		Spot spawn = new Spot(originX + 0.5D, sea + SUMMIT + 1.0D, originZ + 2.5D, 0.0F, 0.0F);
		Spot queuePoint = new Spot(pedestalX + 0.5D, pedestalY + 1.0D, pedestalZ + 0.5D, 180.0F, 0.0F);

		return new Plan(List.copyOf(site.order), spawn, queuePoint, course);
	}

	public static Result build(ServerWorld world, Spot centre) {
		int originX = (int) Math.floor(centre.x());
		int originZ = (int) Math.floor(centre.z());
		// The plaza is where the player stands, so the water sits SUMMIT below it.
		int sea = (int) Math.floor(centre.y()) - SUMMIT;

		Plan plan = plan(originX, originZ, sea);

		for (Placement placement : plan.placements()) {
			world.setBlockState(new BlockPos(placement.x(), placement.y(), placement.z()),
					block(placement.material()).getDefaultState());
		}

		LobbyState state = LobbyState.get();
		state.setSpawn(plan.spawn());
		state.setQueuePoint(plan.queuePoint());
		state.putCourse(plan.course());

		FreezeMute.LOGGER.info("Lobby: built the island at {} {} {} - {} blocks, spawn {}, queue point {}",
				originX, sea, originZ, plan.placements().size(), plan.spawn().describe(),
				plan.queuePoint().describe());

		return new Result(plan.placements().size(), plan.spawn(), plan.queuePoint(), plan.course());
	}

	/** The only place in the island that knows what a Minecraft block is. */
	private static Block block(Material material) {
		return switch (material) {
			case AIR -> Blocks.AIR;
			case WATER -> Blocks.WATER;
			case STONE -> Blocks.STONE;
			case ANDESITE -> Blocks.ANDESITE;
			case POLISHED_ANDESITE -> Blocks.POLISHED_ANDESITE;
			case SMOOTH_STONE -> Blocks.SMOOTH_STONE;
			case MOSSY_COBBLESTONE -> Blocks.MOSSY_COBBLESTONE;
			case DIRT -> Blocks.DIRT;
			case GRASS -> Blocks.GRASS_BLOCK;
			case SAND -> Blocks.SAND;
			case JUNGLE_LOG -> Blocks.JUNGLE_LOG;
			case JUNGLE_LEAVES -> Blocks.JUNGLE_LEAVES;
			case OAK_LOG -> Blocks.OAK_LOG;
			case OAK_PLANKS -> Blocks.OAK_PLANKS;
			case OAK_FENCE -> Blocks.OAK_FENCE;
			case WHITE_CONCRETE -> Blocks.WHITE_CONCRETE;
			case RED_CONCRETE -> Blocks.RED_CONCRETE;
			case WHITE_WOOL -> Blocks.WHITE_WOOL;
			case RED_WOOL -> Blocks.RED_WOOL;
			case BLUE_WOOL -> Blocks.BLUE_WOOL;
			case ORANGE_WOOL -> Blocks.ORANGE_WOOL;
			case YELLOW_WOOL -> Blocks.YELLOW_WOOL;
			case SEA_LANTERN -> Blocks.SEA_LANTERN;
			case POLISHED_BLACKSTONE -> Blocks.POLISHED_BLACKSTONE;
			case POLISHED_BLACKSTONE_BRICKS -> Blocks.POLISHED_BLACKSTONE_BRICKS;
			case QUARTZ -> Blocks.QUARTZ_BLOCK;
			case GOLD -> Blocks.GOLD_BLOCK;
			case EMERALD -> Blocks.EMERALD_BLOCK;
			case DIAMOND -> Blocks.DIAMOND_BLOCK;
		};
	}

	/** One block of the parkour, in world coordinates. */
	public record Step(int x, int y, int z, boolean start, boolean checkpoint, boolean finish) {
		public boolean pad() {
			return start || checkpoint || finish;
		}
	}

	/**
	 * Where every jump of the course goes.
	 *
	 * <p>Separated from the placing so the shape can be checked without a world to place it in.
	 * Whether the jumps are actually jumpable is the only thing about this course that really
	 * matters, and it is worth being able to say so from a test rather than from confidence.
	 */
	public static List<Step> courseSteps(int originX, int originZ, int baseY) {
		List<Step> steps = new ArrayList<>();
		double angle = Math.PI;
		int x = originX + (int) Math.round(COURSE_RING * Math.cos(angle));
		int z = originZ + (int) Math.round(COURSE_RING * Math.sin(angle));
		int y = baseY;

		steps.add(new Step(x, y, z, true, false, false));

		for (int index = 1; index <= COURSE_STEPS; index++) {
			boolean rises = (index - 1) % COURSE_RISE_EVERY == COURSE_RISE_EVERY - 1;
			// Going up costs reach, so the jumps that climb are set closer together. Working
			// forwards until the gap is big enough, rather than dividing the circle and rounding
			// afterwards, is what keeps that true: rounding a chord to whole blocks can add most
			// of a block to it, which is the difference between a jump and a swim.
			double wanted = rises ? 2.2D : 2.6D;
			int nextX = x;
			int nextZ = z;

			for (int sweep = 0; sweep < 2000; sweep++) {
				angle += 0.005D;
				int candidateX = originX + (int) Math.round(COURSE_RING * Math.cos(angle));
				int candidateZ = originZ + (int) Math.round(COURSE_RING * Math.sin(angle));
				double dx = candidateX - x;
				double dz = candidateZ - z;

				if (Math.sqrt(dx * dx + dz * dz) >= wanted) {
					nextX = candidateX;
					nextZ = candidateZ;
					break;
				}
			}

			x = nextX;
			z = nextZ;

			if (rises) {
				y++;
			}

			boolean finish = index == COURSE_STEPS;
			steps.add(new Step(x, y, z, false, !finish && index % 8 == 0, finish));
		}

		return steps;
	}

	/** One island being laid, holding the numbers every part of it needs. */
	private static final class Site {
		private final List<Placement> order = new ArrayList<>();
		/** The island as it stands so far, so a later step can ask what an earlier one left. */
		private final Map<Long, Material> current = new HashMap<>();
		private final int originX;
		private final int originZ;
		private final int sea;
		private final Random random;

		Site(int originX, int originZ, int sea, Random random) {
			this.originX = originX;
			this.originZ = originZ;
			this.sea = sea;
			this.random = random;
		}

		// ------------------------------------------------------------ the shape

		/** How far the shore reaches in a direction. Wobbled, so the island is not a dinner plate. */
		private double coastAt(double angle) {
			return COAST + 4.5D * Math.sin(3.0D * angle + 0.6D) + 2.5D * Math.cos(5.0D * angle + 2.1D);
		}

		/**
		 * The height of the ground at a point, or {@link Integer#MIN_VALUE} out past the shore.
		 *
		 * <p>Quantised into three levels rather than sloped smoothly, so the island has cliffs and
		 * plateaus to build on instead of one continuous hill.
		 */
		private int groundAt(int dx, int dz) {
			double distance = Math.sqrt(dx * dx + dz * dz);
			double coast = coastAt(Math.atan2(dz, dx));

			if (distance > coast) {
				return Integer.MIN_VALUE;
			}

			double inland = (coast - distance) / coast;
			int height;

			if (inland < 0.10D) {
				height = 1;
			} else if (inland < 0.34D) {
				height = 5;
			} else if (inland < 0.62D) {
				height = 9;
			} else {
				height = SUMMIT;
			}

			// A little roughness, so the cliff edges are not drawn with a compass.
			height += (int) Math.round(1.4D * Math.sin(dx * 0.42D) * Math.cos(dz * 0.37D));
			return sea + Math.max(1, height);
		}

		private boolean beach(int dx, int dz) {
			double distance = Math.sqrt(dx * dx + dz * dz);
			double coast = coastAt(Math.atan2(dz, dx));
			return (coast - distance) / coast < 0.14D;
		}

		// ------------------------------------------------------------ the ground

		int ground() {
			int placed = 0;

			for (int dx = -WATER_RADIUS; dx <= WATER_RADIUS; dx++) {
				for (int dz = -WATER_RADIUS; dz <= WATER_RADIUS; dz++) {
					double distance = Math.sqrt(dx * dx + dz * dz);

					if (distance > WATER_RADIUS) {
						continue;
					}

					int top = groundAt(dx, dz);

					if (top != Integer.MIN_VALUE) {
						placed += land(dx, dz, top, beach(dx, dz));
						continue;
					}

					placed += seabed(dx, dz, distance);
				}
			}

			return placed;
		}

		/** A column of island: stone, then dirt, then grass - or sand where the beach reaches. */
		private int land(int dx, int dz, int top, boolean beach) {
			double distance = Math.sqrt(dx * dx + dz * dz);
			// The underside tapers, so the island reads as floating rather than sawn off.
			int bottom = sea - 4 - (int) Math.round(8.0D * Math.max(0.0D, 1.0D - distance / COAST));
			int placed = 0;

			for (int y = bottom; y <= top; y++) {
				Material block;

				if (y == top) {
					block = beach ? Material.SAND : Material.GRASS;
				} else if (y >= top - 2) {
					block = beach ? Material.SAND : Material.DIRT;
				} else if (y <= bottom + 1) {
					block = Material.ANDESITE;
				} else {
					block = Material.STONE;
				}

				placed += set(dx, y, dz, block);
			}

			return placed;
		}

		/**
		 * The lagoon floor, rising back to the surface at the outside.
		 *
		 * <p>That rise is not decoration: it is what holds the water in. Without a rim level with
		 * the surface the whole lagoon pours off the edge of the island and keeps pouring.
		 */
		private int seabed(int dx, int dz, double distance) {
			double shoal = (distance - COAST) / (WATER_RADIUS - COAST);
			int floor = sea - 4 + (int) Math.round(4.0D * Math.max(0.0D, Math.min(1.0D, shoal)));
			int placed = 0;

			for (int y = sea - 7; y <= floor; y++) {
				placed += set(dx, y, dz, y >= floor - 1 ? Material.SAND : Material.ANDESITE);
			}

			return placed;
		}

		/** Fills the lagoon. Done last, so every wall that holds it is already standing. */
		int water() {
			int placed = 0;

			for (int dx = -WATER_RADIUS; dx <= WATER_RADIUS; dx++) {
				for (int dz = -WATER_RADIUS; dz <= WATER_RADIUS; dz++) {
					if (Math.sqrt(dx * dx + dz * dz) > WATER_RADIUS || groundAt(dx, dz) != Integer.MIN_VALUE) {
						// Only the lagoon. Inside the shore the island's underside tapers away,
						// and filling the air below it would hang water off the bottom.
						continue;
					}

					for (int y = sea - 6; y <= sea; y++) {
						if (materialAt(originX + dx, y, originZ + dz) == Material.AIR) {
							placed += set(dx, y, dz, Material.WATER);
						}
					}
				}
			}

			return placed;
		}

		// ----------------------------------------------------------- the middle

		/** The flat top everybody stands on, and the steps down off it. */
		int plaza() {
			int y = sea + SUMMIT;
			int placed = 0;

			for (int dx = -PLAZA_RADIUS - 1; dx <= PLAZA_RADIUS + 1; dx++) {
				for (int dz = -PLAZA_RADIUS - 1; dz <= PLAZA_RADIUS + 1; dz++) {
					double distance = Math.sqrt(dx * dx + dz * dz);

					if (distance > PLAZA_RADIUS + 1) {
						continue;
					}

					Material block = distance > PLAZA_RADIUS - 1 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;

					if (distance > PLAZA_RADIUS) {
						block = Material.MOSSY_COBBLESTONE;
					}

					placed += set(dx, y, dz, block);

					// Anything the terrain left standing where the plaza goes. Cleared without
					// asking first, so it also empties a room built here before this one.
					for (int height = 1; height <= 6; height++) {
						placed += set(dx, y + height, dz, Material.AIR);
					}
				}
			}

			return placed;
		}

		/**
		 * Where the NPC goes. Nothing is spawned on it: whatever people use for NPCs is their own
		 * business, and a right click anywhere near this spot joins the queue either way.
		 */
		int pedestal(int middleX, int middleY, int middleZ) {
			int placed = 0;

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean corner = dx != 0 && dz != 0;
					placed += put(middleX + dx, middleY, middleZ + dz,
							corner ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
					// Standing room on top of it, whatever the plaza left there.
					placed += put(middleX + dx, middleY + 1, middleZ + dz, Material.AIR);
					placed += put(middleX + dx, middleY + 2, middleZ + dz, Material.AIR);
				}
			}

			placed += put(middleX - 2, middleY, middleZ, Material.OAK_FENCE);
			placed += put(middleX - 2, middleY + 1, middleZ, Material.SEA_LANTERN);
			placed += put(middleX + 2, middleY, middleZ, Material.OAK_FENCE);
			placed += put(middleX + 2, middleY + 1, middleZ, Material.SEA_LANTERN);
			return placed;
		}

		// ---------------------------------------------------------- the planting

		int palms() {
			int placed = 0;
			List<int[]> planted = new ArrayList<>();

			for (int attempt = 0; attempt < 220 && planted.size() < 16; attempt++) {
				int dx = random.nextInt(2 * (int) COAST) - (int) COAST;
				int dz = random.nextInt(2 * (int) COAST) - (int) COAST;
				int top = groundAt(dx, dz);

				if (top == Integer.MIN_VALUE || Math.sqrt(dx * dx + dz * dz) < PLAZA_RADIUS + 4) {
					continue;
				}

				boolean crowded = false;

				for (int[] other : planted) {
					if (Math.abs(other[0] - dx) < 6 && Math.abs(other[1] - dz) < 6) {
						crowded = true;
						break;
					}
				}

				if (crowded) {
					continue;
				}

				planted.add(new int[] { dx, dz });
				placed += palm(dx, top, dz);
			}

			return placed;
		}

		/** A palm: a trunk that leans a little, and fronds hanging off the top of it. */
		private int palm(int dx, int base, int dz) {
			int height = 5 + random.nextInt(4);
			int leanX = random.nextInt(3) - 1;
			int leanZ = random.nextInt(3) - 1;
			int placed = 0;
			int x = dx;
			int z = dz;

			for (int step = 1; step <= height; step++) {
				if (step > height - 3) {
					x += step % 2 == 0 ? leanX : 0;
					z += step % 2 == 1 ? leanZ : 0;
				}

				placed += set(x, base + step, z, Material.JUNGLE_LOG);
			}

			int crown = base + height;
			placed += set(x, crown + 1, z, Material.JUNGLE_LEAVES);

			// Four fronds, each running out level and then stepping down at the tip.
			//
			// The step down needs a leaf under the one before it as well. Minecraft measures how
			// far a leaf is from its tree by stepping between blocks that touch face to face, and
			// rots anything more than six steps away - a tip that sits diagonally off the end of
			// the frond touches it at a corner only, which is not a connection at all, so every
			// palm would quietly drop its tips.
			int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

			for (int[] direction : directions) {
				int outX = direction[0];
				int outZ = direction[1];

				placed += set(x + outX, crown, z + outZ, Material.JUNGLE_LEAVES);
				placed += set(x + outX * 2, crown, z + outZ * 2, Material.JUNGLE_LEAVES);
				placed += set(x + outX * 2, crown - 1, z + outZ * 2, Material.JUNGLE_LEAVES);
				placed += set(x + outX * 3, crown - 1, z + outZ * 3, Material.JUNGLE_LEAVES);
			}

			return placed;
		}

		int rocks() {
			int placed = 0;

			for (int count = 0; count < 12; count++) {
				int dx = random.nextInt(2 * (int) COAST) - (int) COAST;
				int dz = random.nextInt(2 * (int) COAST) - (int) COAST;
				int top = groundAt(dx, dz);

				if (top == Integer.MIN_VALUE || Math.sqrt(dx * dx + dz * dz) < PLAZA_RADIUS + 3) {
					continue;
				}

				int size = 1 + random.nextInt(2);

				for (int ox = -size; ox <= size; ox++) {
					for (int oz = -size; oz <= size; oz++) {
						for (int oy = 0; oy <= size; oy++) {
							if (ox * ox + oz * oz + oy * oy > size * size + 1) {
								continue;
							}

							placed += set(dx + ox, top + oy, dz + oz,
									random.nextInt(3) == 0 ? Material.MOSSY_COBBLESTONE : Material.ANDESITE);
						}
					}
				}
			}

			return placed;
		}

		// ------------------------------------------------------------ the built

		/** A jetty walking out south over the lagoon, on stilts. */
		int jetty() {
			int deck = sea + 2;
			int placed = 0;

			int shore = (int) Math.floor(coastAt(Math.PI / 2.0D)) - 2;

			for (int reach = shore; reach <= WATER_RADIUS - 3; reach++) {
				int dz = reach;

				for (int dx = -1; dx <= 1; dx++) {
					placed += set(dx, deck, dz, Material.OAK_PLANKS);

					for (int height = 1; height <= 6; height++) {
						placed += set(dx, deck + height, dz, Material.AIR);
					}
				}

				if (reach % 5 == 0) {
					for (int dx = -1; dx <= 1; dx += 2) {
						for (int y = sea - 4; y < deck; y++) {
							placed += set(dx, y, dz, Material.OAK_LOG);
						}

						placed += set(dx, deck + 1, dz, Material.OAK_FENCE);
						placed += set(dx, deck + 2, dz, Material.SEA_LANTERN);
					}
				} else {
					placed += set(-1, deck + 1, dz, Material.OAK_FENCE);
					placed += set(1, deck + 1, dz, Material.OAK_FENCE);
				}
			}

			return placed;
		}

		/** A banded tower on the west shore, with a lit room at the top. */
		int lighthouse() {
			// Off the line the parkour spiral takes, so the two do not grow through each other.
			int dx = -16;
			int dz = -16;
			int base = groundAt(dx, dz);

			if (base == Integer.MIN_VALUE) {
				base = sea + 1;
			}

			int height = 18;
            int placed = 0;

			for (int y = 0; y <= height; y++) {
				int radius = y > height - 4 ? 3 : 2;
				boolean band = (y / 3) % 2 == 0;

				for (int ox = -radius; ox <= radius; ox++) {
					for (int oz = -radius; oz <= radius; oz++) {
						double distance = Math.sqrt(ox * ox + oz * oz);

						if (distance > radius + 0.4D) {
							continue;
						}

						boolean shell = distance > radius - 0.9D;

						if (y == height) {
							placed += set(dx + ox, base + y, dz + oz, Material.POLISHED_ANDESITE);
						} else if (y == height - 2 && !shell) {
							placed += set(dx + ox, base + y, dz + oz, Material.SEA_LANTERN);
						} else if (shell) {
							placed += set(dx + ox, base + y, dz + oz,
									y > height - 4 ? Material.OAK_FENCE
											: band ? Material.WHITE_CONCRETE : Material.RED_CONCRETE);
						} else if (y == 0) {
							placed += set(dx + ox, base + y, dz + oz, Material.STONE);
						}
					}
				}
			}

			return placed;
		}

		/** A shelter beside the plaza: four posts and a stepped roof. */
		int pavilion() {
			int dx = 12;
			int dz = -6;
			int base = groundAt(dx, dz);

			if (base == Integer.MIN_VALUE) {
				return 0;
			}

			int placed = 0;

			for (int ox = -3; ox <= 3; ox++) {
				for (int oz = -3; oz <= 3; oz++) {
					placed += set(dx + ox, base, dz + oz, Material.OAK_PLANKS);
				}
			}

			for (int corner = 0; corner < 4; corner++) {
				int ox = (corner & 1) == 0 ? -3 : 3;
				int oz = (corner & 2) == 0 ? -3 : 3;

				for (int y = 1; y <= 4; y++) {
					placed += set(dx + ox, base + y, dz + oz, Material.OAK_LOG);
				}
			}

			for (int layer = 0; layer <= 3; layer++) {
				int reach = 3 - layer;

				for (int ox = -reach; ox <= reach; ox++) {
					for (int oz = -reach; oz <= reach; oz++) {
						if (layer > 0 && Math.abs(ox) < reach && Math.abs(oz) < reach) {
							continue;
						}

						placed += set(dx + ox, base + 5 + layer, dz + oz, Material.RED_CONCRETE);
					}
				}
			}

			return placed;
		}

		/** Balloons overhead, because the sky above an island should have something in it. */
		int balloons() {
			Material[] colours = { Material.RED_WOOL, Material.BLUE_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL };
			int placed = 0;

			for (int count = 0; count < 4; count++) {
				double angle = count * Math.PI / 2.0D + 0.7D;
				int dx = (int) Math.round(Math.cos(angle) * (COAST - 6));
				int dz = (int) Math.round(Math.sin(angle) * (COAST - 6));
				int y = sea + 30 + random.nextInt(10);
				Material skin = colours[count % colours.length];
				int radius = 4;

				for (int ox = -radius; ox <= radius; ox++) {
					for (int oy = -radius; oy <= radius; oy++) {
						for (int oz = -radius; oz <= radius; oz++) {
							double distance = Math.sqrt(ox * ox + oy * oy * 0.7D + oz * oz);

							if (distance > radius || distance < radius - 1.0D) {
								continue;
							}

							boolean stripe = ((ox + oz + radius) / 2) % 2 == 0;
							placed += set(dx + ox, y + oy, dz + oz, stripe ? skin : Material.WHITE_WOOL);
						}
					}
				}

				for (int rope = 1; rope <= 3; rope++) {
					placed += set(dx, y - radius - rope, dz, Material.OAK_FENCE);
				}

				for (int ox = -1; ox <= 1; ox++) {
					for (int oz = -1; oz <= 1; oz++) {
						placed += set(dx + ox, y - radius - 4, dz + oz, Material.OAK_PLANKS);
					}
				}
			}

			return placed;
		}

		// ---------------------------------------------------------- the parkour

		/** A rising spiral out over the lagoon, registered as a course as it is laid. */
		Course course() {
			List<Step> steps = courseSteps(originX, originZ, sea + SUMMIT + 2);
			List<Spot> pads = new ArrayList<>();
			int blocks = 0;

			for (Step step : steps) {
				Material block = Material.QUARTZ;

				if (step.start()) {
					block = Material.GOLD;
				} else if (step.finish()) {
					block = Material.DIAMOND;
				} else if (step.checkpoint()) {
					block = Material.EMERALD;
				}

				put(step.x(), step.y(), step.z(), block);
				blocks++;

				if (step.pad()) {
					pads.add(new Spot(step.x() + 0.5D, step.y() + 1.0D, step.z() + 0.5D, 0.0F, 0.0F));
				}
			}

			Course course = Course.starting(COURSE_NAME, pads.get(0));

			for (int index = 1; index < pads.size() - 1; index++) {
				course = course.withCheckpoint(pads.get(index));
			}

			return course.withFinish(pads.get(pads.size() - 1));
		}

		// ----------------------------------------------------------- the basics

		/** Coordinates packed into one long, so the plan can be looked up without allocating. */
		private static long key(int x, int y, int z) {
			return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y + 2048L) & 0xFFFL;
		}

		private Material materialAt(int x, int y, int z) {
			return current.getOrDefault(key(x, y, z), Material.AIR);
		}

		/** Relative to the middle of the island. */
		private int set(int dx, int y, int dz, Material material) {
			return put(originX + dx, y, originZ + dz, material);
		}

		/** In world coordinates. */
		private int put(int x, int y, int z, Material material) {
			order.add(new Placement(x, y, z, material));
			current.put(key(x, y, z), material);
			return 1;
		}
	}
}
