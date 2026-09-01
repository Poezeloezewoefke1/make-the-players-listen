package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dev.mtpl.freezemute.FreezeMute;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
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
	private static final double COAST = 58.0D;
	/** The lagoon and the shoal that holds it in. */
	private static final int WATER_RADIUS = 78;
	/** Height of the flat top the plaza sits on, above the water. */
	private static final int SUMMIT = 20;
	private static final int PLAZA_RADIUS = 12;
	/** The terrace one step down from the plaza, where most of the town stands. */
	private static final int TERRACE = 13;
	/**
	 * How far out from the middle the four town buildings sit.
	 *
	 * <p>On the diagonals, so each one has a clear run back to a corner of the plaza. Far enough
	 * out that a building pad clears the plaza rim, near enough in that the far edge of a pad is
	 * still well inside the closest the shore ever comes. {@code IslandPlanTest} holds both ends
	 * of that to account.
	 */
	private static final int TOWN_OUT = 24;
	private static final int TOWN_PAD = 11;

	private static final int COURSE_STEPS = 24;
	private static final double COURSE_RING = 19.0D;
	private static final double COURSE_GAP = 3.0D;
	private static final int COURSE_RISE_EVERY = 3;
	public static final String COURSE_NAME = "spawn";

	private LobbyBuilder() {
	}

	/**
	 * How far above the water the plaza sits, which is also how far below the command's feet the
	 * sea ends up. The command tells people to stand where they want the top of the island.
	 */
	public static int summit() {
		return SUMMIT;
	}

	/** How far out the build reaches. Everything inside this is replaced. */
	public static int reach() {
		return WATER_RADIUS;
	}

	/**
	 * How far the shore reaches in a direction. Wobbled, so the island is not a dinner plate.
	 *
	 * <p>Kept to about ten blocks either way on purpose. The town is laid out by number, and a
	 * shore that could swing thirty would put a building pad over open water - where levelling a
	 * footing means filling the lagoon with a pillar of stone. Public because that constraint has
	 * two ends and a test should be able to read both of them rather than be told one as a number.
	 */
	public static double shoreAt(double angle) {
		return COAST
				+ 6.0D * Math.sin(3.0D * angle + 0.6D)
				+ 3.0D * Math.cos(5.0D * angle + 2.1D)
				+ 1.5D * Math.sin(8.0D * angle + 1.3D);
	}

	/** Where the four town buildings stand, relative to the middle, and how wide their footings are. */
	public static int townOut() {
		return TOWN_OUT;
	}

	public static int townPad() {
		return TOWN_PAD;
	}

	/** The height the town stands at, above the water. */
	public static int terrace() {
		return TERRACE;
	}

	/** How far the plaza reaches, rim included. */
	public static int plazaRadius() {
		return PLAZA_RADIUS;
	}

	/** What a build did, so the command can say something useful. */
	public record Result(int blocks, Spot spawn, Spot queuePoint, Course course) {
	}

	/** One block of the island, in world coordinates. Applied in order, so later ones win. */
	public record Placement(int x, int y, int z, Material material) {
	}

	/**
	 * Everything a build decided, before any of it touches a world.
	 *
	 * <p>{@code balloons} is where the middle of each one ended up. It is carried because there is
	 * no other honest way to find them afterwards: they are made of wool, and so are the market
	 * canopies and the flower beds, so a test that went looking for wool would be checking the
	 * height of a market stall and calling it a balloon.
	 */
	public record Plan(List<Placement> placements, Spot spawn, Spot queuePoint, Course course,
			List<Spot> balloons) {
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

		// Order is load bearing. Everything that levels its own footing also clears the sky above
		// it, so anything placed before it in the same column is wiped: the ground first, then the
		// things that flatten it, then the paths joining them, and only then the trees, the rocks
		// and the trimmings that have to survive. Water is last of all, once every wall that holds
		// it is standing.
		site.ground();

		site.greatHall(-TOWN_OUT, TOWN_OUT);
		site.market(TOWN_OUT, TOWN_OUT);
		site.watchtower(TOWN_OUT, -TOWN_OUT);
		site.gardens(-TOWN_OUT, -TOWN_OUT);

		site.plaza();
		site.townPaths();

		site.palms();
		site.rocks();
		site.jetty();
		site.lighthouse();
		site.pavilion();
		site.fountain(0, 6);
		site.lamps();
		site.balloons();
		site.water();

		// The plaza reads north to south: the pedestal somebody walks up to, the spot they arrive
		// on, and the fountain behind it. Nothing overlaps the ring of lamps around the rim.
		int pedestalX = originX;
		int pedestalY = sea + SUMMIT + 1;
		int pedestalZ = originZ - 7;
		site.pedestal(pedestalX, pedestalY, pedestalZ);

		Course course = site.course();

		// Facing each other. The pedestal is north of the spawn, so somebody arriving looks north
		// - yaw 180 - and the figure on it looks south, at them. Both were the other way round,
		// which put the first thing anybody was meant to see behind them and gave the figure its
		// back to the whole plaza.
		Spot spawn = new Spot(originX + 0.5D, sea + SUMMIT + 1.0D, originZ - 1.5D, 180.0F, 0.0F);
		Spot queuePoint = new Spot(pedestalX + 0.5D, pedestalY + 1.0D, pedestalZ + 0.5D, 0.0F, 0.0F);

		return new Plan(List.copyOf(site.order), spawn, queuePoint, course, List.copyOf(site.balloonCentres));
	}

	/** The build in progress, or null. One at a time: two islands in one place is neither. */
	private static volatile BuildJob job;

	/**
	 * Starts laying an island, and returns straight away.
	 *
	 * <p>The plan is worked out in full here - it is a list, and cheap - but the blocks are laid a
	 * slice per tick by {@link #tickBuild}. See {@link BuildJob} for why. The spawn, the queue
	 * point and the course are recorded now, because they are the answer to "where will things
	 * be" and the command has to be able to say so; what waits for the last block is moving
	 * anybody onto them.
	 */
	public static Result build(ServerWorld world, Spot centre, Runnable whenDone) {
		int originX = (int) Math.floor(centre.x());
		int originZ = (int) Math.floor(centre.z());
		// The plaza is where the player stands, so the water sits SUMMIT below it.
		int sea = (int) Math.floor(centre.y()) - SUMMIT;

		Plan plan = plan(originX, originZ, sea);

		LobbyState state = LobbyState.get();
		state.setSpawn(plan.spawn());
		state.setQueuePoint(plan.queuePoint());
		state.putCourse(plan.course());

		BuildJob started = new BuildJob(world, plan.placements(), LobbyBuilder::lay, whenDone);

		FreezeMute.LOGGER.info("Lobby: laying an island at {} {} {} - {} blocks, spawn {}, queue point {}",
				originX, sea, originZ, plan.placements().size(), plan.spawn().describe(),
				plan.queuePoint().describe());

		if (nobodyOnline()) {
			// Two reasons, and either would do on its own. There is nobody whose game a long tick
			// would spoil, so there is nothing to protect by going slowly. And a server with
			// nobody on it may not be ticking at all - vanilla pauses an empty one after a
			// minute - so a job handed to the tick loop would sit there unfinished for ever,
			// leaving half an island and a build that never says it stopped.
			FreezeMute.LOGGER.info("Lobby: nobody is online, so it goes down in one go");

			while (!started.done()) {
				started.tick();
			}

			started.finish();
			return new Result(plan.placements().size(), plan.spawn(), plan.queuePoint(), plan.course());
		}

		job = started;
		return new Result(plan.placements().size(), plan.spawn(), plan.queuePoint(), plan.course());
	}

	/** True while an island is still going down. */
	public static boolean building() {
		return job != null;
	}

	/** How far along, for something a person is watching. */
	public static int progress() {
		BuildJob current = job;
		return current == null ? 100 : current.percent();
	}

	/** One slice. Called every tick; does nothing when there is no build running. */
	public static void tickBuild() {
		BuildJob current = job;

		if (current == null) {
			return;
		}

		current.tick();

		if (current.done()) {
			// Cleared before the completion runs, so anything it does sees a finished island
			// rather than one still officially in progress.
			job = null;
			current.finish();
		}
	}

	/** Whether there is anybody whose game a long tick would spoil. */
	private static boolean nobodyOnline() {
		MinecraftServer server = FreezeMute.server();
		return server == null || server.getPlayerManager().getPlayerList().isEmpty();
	}

	private static void lay(Object world, Placement placement) {
		((ServerWorld) world).setBlockState(new BlockPos(placement.x(), placement.y(), placement.z()),
				block(placement.material()).getDefaultState());
	}

	/** The only place in the island that knows what a Minecraft block is. */
	private static Block block(Material material) {
		return switch (material) {
			case AIR -> Blocks.AIR;
			case WATER -> Blocks.WATER;

			case STONE -> Blocks.STONE;
			case COBBLESTONE -> Blocks.COBBLESTONE;
			case MOSSY_COBBLESTONE -> Blocks.MOSSY_COBBLESTONE;
			case ANDESITE -> Blocks.ANDESITE;
			case POLISHED_ANDESITE -> Blocks.POLISHED_ANDESITE;
			case DIORITE -> Blocks.DIORITE;
			case POLISHED_DIORITE -> Blocks.POLISHED_DIORITE;
			case GRANITE -> Blocks.GRANITE;
			case POLISHED_GRANITE -> Blocks.POLISHED_GRANITE;
			case SMOOTH_STONE -> Blocks.SMOOTH_STONE;
			case STONE_BRICKS -> Blocks.STONE_BRICKS;
			case MOSSY_STONE_BRICKS -> Blocks.MOSSY_STONE_BRICKS;
			case CRACKED_STONE_BRICKS -> Blocks.CRACKED_STONE_BRICKS;
			case CHISELED_STONE_BRICKS -> Blocks.CHISELED_STONE_BRICKS;
			case DEEPSLATE -> Blocks.DEEPSLATE;
			case POLISHED_DEEPSLATE -> Blocks.POLISHED_DEEPSLATE;
			case DEEPSLATE_BRICKS -> Blocks.DEEPSLATE_BRICKS;
			case TUFF -> Blocks.TUFF;
			case CALCITE -> Blocks.CALCITE;
			case BLACKSTONE -> Blocks.BLACKSTONE;
			case POLISHED_BLACKSTONE -> Blocks.POLISHED_BLACKSTONE;
			case POLISHED_BLACKSTONE_BRICKS -> Blocks.POLISHED_BLACKSTONE_BRICKS;
			case BRICKS -> Blocks.BRICKS;

			case DIRT -> Blocks.DIRT;
			case COARSE_DIRT -> Blocks.COARSE_DIRT;
			case PODZOL -> Blocks.PODZOL;
			case GRASS -> Blocks.GRASS_BLOCK;
			case MOSS_BLOCK -> Blocks.MOSS_BLOCK;
			case SAND -> Blocks.SAND;
			case SANDSTONE -> Blocks.SANDSTONE;
			case SMOOTH_SANDSTONE -> Blocks.SMOOTH_SANDSTONE;
			case GRAVEL -> Blocks.GRAVEL;
			case CLAY -> Blocks.CLAY;

			case OAK_LOG -> Blocks.OAK_LOG;
			case OAK_PLANKS -> Blocks.OAK_PLANKS;
			case OAK_FENCE -> Blocks.OAK_FENCE;
			case OAK_LEAVES -> Blocks.OAK_LEAVES;
			case STRIPPED_OAK_LOG -> Blocks.STRIPPED_OAK_LOG;
			case SPRUCE_LOG -> Blocks.SPRUCE_LOG;
			case SPRUCE_PLANKS -> Blocks.SPRUCE_PLANKS;
			case SPRUCE_FENCE -> Blocks.SPRUCE_FENCE;
			case SPRUCE_LEAVES -> Blocks.SPRUCE_LEAVES;
			case DARK_OAK_LOG -> Blocks.DARK_OAK_LOG;
			case DARK_OAK_PLANKS -> Blocks.DARK_OAK_PLANKS;
			case BIRCH_LOG -> Blocks.BIRCH_LOG;
			case BIRCH_PLANKS -> Blocks.BIRCH_PLANKS;
			case BIRCH_LEAVES -> Blocks.BIRCH_LEAVES;
			case JUNGLE_LOG -> Blocks.JUNGLE_LOG;
			case JUNGLE_PLANKS -> Blocks.JUNGLE_PLANKS;
			case JUNGLE_LEAVES -> Blocks.JUNGLE_LEAVES;
			case BOOKSHELF -> Blocks.BOOKSHELF;

			case WHITE_CONCRETE -> Blocks.WHITE_CONCRETE;
			case RED_CONCRETE -> Blocks.RED_CONCRETE;
			case BLUE_CONCRETE -> Blocks.BLUE_CONCRETE;
			case YELLOW_CONCRETE -> Blocks.YELLOW_CONCRETE;
			case ORANGE_CONCRETE -> Blocks.ORANGE_CONCRETE;
			case LIME_CONCRETE -> Blocks.LIME_CONCRETE;
			case BLACK_CONCRETE -> Blocks.BLACK_CONCRETE;
			case WHITE_WOOL -> Blocks.WHITE_WOOL;
			case RED_WOOL -> Blocks.RED_WOOL;
			case BLUE_WOOL -> Blocks.BLUE_WOOL;
			case ORANGE_WOOL -> Blocks.ORANGE_WOOL;
			case YELLOW_WOOL -> Blocks.YELLOW_WOOL;
			case GREEN_WOOL -> Blocks.GREEN_WOOL;
			case PURPLE_WOOL -> Blocks.PURPLE_WOOL;
			case WHITE_TERRACOTTA -> Blocks.WHITE_TERRACOTTA;
			case ORANGE_TERRACOTTA -> Blocks.ORANGE_TERRACOTTA;
			case RED_TERRACOTTA -> Blocks.RED_TERRACOTTA;

			case PRISMARINE -> Blocks.PRISMARINE;
			case PRISMARINE_BRICKS -> Blocks.PRISMARINE_BRICKS;
			case DARK_PRISMARINE -> Blocks.DARK_PRISMARINE;
			case COPPER_BLOCK -> Blocks.COPPER_BLOCK;
			case OXIDIZED_COPPER -> Blocks.OXIDIZED_COPPER;
			case CUT_COPPER -> Blocks.CUT_COPPER;

			case SEA_LANTERN -> Blocks.SEA_LANTERN;
			case GLOWSTONE -> Blocks.GLOWSTONE;
			case SHROOMLIGHT -> Blocks.SHROOMLIGHT;
			case OCHRE_FROGLIGHT -> Blocks.OCHRE_FROGLIGHT;
			case VERDANT_FROGLIGHT -> Blocks.VERDANT_FROGLIGHT;

			case GLASS -> Blocks.GLASS;
			case WHITE_STAINED_GLASS -> Blocks.WHITE_STAINED_GLASS;
			case LIGHT_BLUE_STAINED_GLASS -> Blocks.LIGHT_BLUE_STAINED_GLASS;
			case BROWN_STAINED_GLASS -> Blocks.BROWN_STAINED_GLASS;

			case QUARTZ -> Blocks.QUARTZ_BLOCK;
			case CHISELED_QUARTZ -> Blocks.CHISELED_QUARTZ_BLOCK;
			case QUARTZ_BRICKS -> Blocks.QUARTZ_BRICKS;
			case GOLD -> Blocks.GOLD_BLOCK;
			case EMERALD -> Blocks.EMERALD_BLOCK;
			case DIAMOND -> Blocks.DIAMOND_BLOCK;
			case AMETHYST -> Blocks.AMETHYST_BLOCK;
			case LAPIS -> Blocks.LAPIS_BLOCK;
			case IRON -> Blocks.IRON_BLOCK;
			case HAY -> Blocks.HAY_BLOCK;
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
		/** Where each balloon ended up, so the plan can say and a test can check. */
		private final List<Spot> balloonCentres = new ArrayList<>();
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
			return shoreAt(angle);
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

			// Five steps rather than three, so the island reads as terraces with cliffs between
			// them - a town needs flat ground to stand on, and more than one level of it.
			if (inland < 0.07D) {
				height = 1;
			} else if (inland < 0.20D) {
				height = 4;
			} else if (inland < 0.36D) {
				height = 9;
			} else if (inland < 0.54D) {
				height = TERRACE;
			} else {
				height = SUMMIT;
			}

			// A little roughness, so the cliff edges are not drawn with a compass.
			height += (int) Math.round(1.6D * Math.sin(dx * 0.42D) * Math.cos(dz * 0.37D));
			return sea + Math.max(1, height);
		}

		/** Flat enough to put a building on, and high enough to be out of the surf. */
		private boolean buildable(int dx, int dz, int wantedHeight, int tolerance) {
			int top = groundAt(dx, dz);
			return top != Integer.MIN_VALUE && Math.abs(top - (sea + wantedHeight)) <= tolerance;
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
			int bottom = sea - 6 - (int) Math.round(14.0D * Math.max(0.0D, 1.0D - distance / COAST));
			int placed = 0;
			// One number per column, so a band of granite runs in a seam rather than speckling.
			double seam = Math.sin(dx * 0.11D) * Math.cos(dz * 0.13D);

			for (int y = bottom; y <= top; y++) {
				Material block;

				if (y == top) {
					block = beach ? Material.SAND : grassAt(dx, dz, top);
				} else if (y >= top - 2) {
					block = beach ? Material.SAND : Material.DIRT;
				} else {
					block = rock(y, bottom, seam);
				}

				placed += set(dx, y, dz, block);
			}

			return placed;
		}

		/**
		 * What the top of a column is when it is not beach.
		 *
		 * <p>Grass everywhere would read as a golf course. The high ground gets podzol and coarse
		 * dirt in patches, and moss where it is damp near the cliffs, so the terraces have some
		 * texture to them from above.
		 */
		private Material grassAt(int dx, int dz, int top) {
			double patch = Math.sin(dx * 0.21D + 1.1D) * Math.cos(dz * 0.19D - 0.4D);
			int above = top - sea;

			if (above >= TERRACE && patch > 0.62D) {
				return Material.PODZOL;
			}

			if (above >= 9 && patch < -0.72D) {
				return Material.COARSE_DIRT;
			}

			if (above <= 5 && patch > 0.78D) {
				return Material.MOSS_BLOCK;
			}

			return Material.GRASS;
		}

		/**
		 * The rock a cliff face is cut out of.
		 *
		 * <p>Banded by depth so an exposed cliff shows strata - deepslate at the roots, tuff and
		 * andesite through the middle, stone up top - with seams of granite and diorite running
		 * through it. A cliff of plain stone twenty blocks high is a wall, not a cliff.
		 */
		private Material rock(int y, int bottom, double seam) {
			int depth = y - bottom;

			if (depth <= 2) {
				return Material.DEEPSLATE;
			}

			if (depth <= 5) {
				return seam > 0.55D ? Material.TUFF : Material.DEEPSLATE;
			}

			if (y < sea - 1) {
				return seam < -0.6D ? Material.TUFF : Material.ANDESITE;
			}

			if (seam > 0.72D) {
				return Material.GRANITE;
			}

			if (seam < -0.78D) {
				return Material.DIORITE;
			}

			return y > sea + 8 && seam > 0.35D ? Material.ANDESITE : Material.STONE;
		}

		// --------------------------------------------------------- the town

		/**
		 * Levels a patch of ground to one height and clears the sky above it.
		 *
		 * <p>The terraces are shaped by noise, so a building dropped on raw ground would stand
		 * half buried in a cliff and half over a drop. Every structure levels its own footing
		 * first - which is what anybody would do before building - and that is what lets the town
		 * be laid out by name and number without caring where the noise put the hillside.
		 *
		 * <p>The fill goes down until it meets something solid, so a pad over a dip gets legs
		 * rather than floating.
		 */
		private int pad(int cx, int cz, int radius, int floorY, Material surface, Material fill) {
			int placed = 0;

			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.sqrt(dx * dx + dz * dz) > radius + 0.5D) {
						continue;
					}

					int x = cx + dx;
					int z = cz + dz;
					placed += set(x, floorY, z, surface);

					for (int y = floorY - 1; y > floorY - 26; y--) {
						if (materialAt(originX + x, y, originZ + z).standable()) {
							break;
						}

						placed += set(x, y, z, fill);
					}

					for (int y = floorY + 1; y <= sea + SUMMIT + 10; y++) {
						placed += set(x, y, z, Material.AIR);
					}
				}
			}

			return placed;
		}

		/** A rectangular room: walls, a floor, a roof and a hollow middle. */
		private int room(int cx, int cz, int floorY, int halfX, int halfZ, int height,
				Material wall, Material corner, Material floor, Material roof) {
			int placed = 0;

			for (int dx = -halfX; dx <= halfX; dx++) {
				for (int dz = -halfZ; dz <= halfZ; dz++) {
					boolean edge = Math.abs(dx) == halfX || Math.abs(dz) == halfZ;
					boolean post = Math.abs(dx) == halfX && Math.abs(dz) == halfZ;

					placed += set(cx + dx, floorY, cz + dz, floor);

					for (int y = 1; y < height; y++) {
						if (!edge) {
							placed += set(cx + dx, floorY + y, cz + dz, Material.AIR);
							continue;
						}

						placed += set(cx + dx, floorY + y, cz + dz, post ? corner : wall);
					}

					placed += set(cx + dx, floorY + height, cz + dz, roof);
				}
			}

			return placed;
		}

		/** Knocks a doorway through a wall, two blocks high. */
		private int doorway(int x, int floorY, int z, int width, boolean alongX) {
			int placed = 0;

			for (int offset = -width; offset <= width; offset++) {
				for (int y = 1; y <= 2; y++) {
					placed += set(alongX ? x + offset : x, floorY + y, alongX ? z : z + offset, Material.AIR);
				}
			}

			return placed;
		}

		/** A window band punched along a wall, so a building is not a windowless box. */
		private int windows(int cx, int cz, int floorY, int halfX, int halfZ, int atHeight, Material glass) {
			int placed = 0;

			for (int dx = -halfX + 2; dx <= halfX - 2; dx += 2) {
				placed += set(cx + dx, floorY + atHeight, cz - halfZ, glass);
				placed += set(cx + dx, floorY + atHeight, cz + halfZ, glass);
			}

			for (int dz = -halfZ + 2; dz <= halfZ - 2; dz += 2) {
				placed += set(cx - halfX, floorY + atHeight, cz + dz, glass);
				placed += set(cx + halfX, floorY + atHeight, cz + dz, glass);
			}

			return placed;
		}

		/**
		 * The fountain on the plaza.
		 *
		 * <p>Water in a basin with a wall all the way round and a floor under it, for the same
		 * reason the lagoon has a rim: an unwalled pool runs out across the plaza and keeps going.
		 */
		int fountain(int cx, int cz) {
			int y = sea + SUMMIT;
			int placed = 0;
			int radius = 4;

			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					double distance = Math.sqrt(dx * dx + dz * dz);

					if (distance > radius + 0.5D) {
						continue;
					}

					// The rim stands one block proud, so the water has something to sit behind.
					if (distance > radius - 1) {
						placed += set(cx + dx, y, cz + dz, Material.POLISHED_DIORITE);
						placed += set(cx + dx, y + 1, cz + dz, Material.CHISELED_STONE_BRICKS);
						continue;
					}

					placed += set(cx + dx, y, cz + dz, Material.PRISMARINE_BRICKS);
					placed += set(cx + dx, y + 1, cz + dz, Material.WATER);
				}
			}

			// A tiered centrepiece standing out of the water.
			for (int step = 0; step < 4; step++) {
				int spread = 2 - step / 2;

				for (int dx = -spread; dx <= spread; dx++) {
					for (int dz = -spread; dz <= spread; dz++) {
						if (Math.abs(dx) + Math.abs(dz) > spread + 1) {
							continue;
						}

						placed += set(cx + dx, y + 1 + step, cz + dz,
								step == 3 ? Material.SEA_LANTERN : Material.QUARTZ_BRICKS);
					}
				}
			}

			// No source block perched on top of the centrepiece. It looks like a spout in a
			// drawing and behaves like a burst pipe in a game: water on a column with nothing
			// beside it runs off all four sides and keeps running.
			return placed;
		}

		/**
		 * The great hall on the terrace: the biggest thing on the island that is not the hill.
		 */
		int greatHall(int cx, int cz) {
			int floorY = sea + TERRACE;
			int placed = pad(cx, cz, TOWN_PAD, floorY, Material.STONE_BRICKS, Material.STONE);

			placed += room(cx, cz, floorY, 9, 7, 8,
					Material.STONE_BRICKS, Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS, Material.DARK_OAK_PLANKS);
			placed += windows(cx, cz, floorY, 9, 7, 4, Material.LIGHT_BLUE_STAINED_GLASS);
			placed += doorway(cx, floorY, cz + 7, 2, true);

			// A pitched roof, laid in shrinking courses so it reads as a roof from outside.
			for (int course = 0; course <= 8; course++) {
				int halfX = 9 - course;
				int halfZ = 7 - course;

				if (halfX < 0 || halfZ < 0) {
					break;
				}

				for (int dx = -halfX; dx <= halfX; dx++) {
					for (int dz = -halfZ; dz <= halfZ; dz++) {
						boolean edge = Math.abs(dx) == halfX || Math.abs(dz) == halfZ;
						placed += set(cx + dx, floorY + 8 + course, cz + dz,
								edge ? Material.DARK_OAK_LOG : Material.AIR);
					}
				}
			}

			// Pillars and lamps inside, so it is a hall rather than a shed.
			for (int dx = -6; dx <= 6; dx += 6) {
				for (int dz = -4; dz <= 4; dz += 4) {
					for (int y = 1; y <= 6; y++) {
						placed += set(cx + dx, floorY + y, cz + dz, Material.STRIPPED_OAK_LOG);
					}

					placed += set(cx + dx, floorY + 7, cz + dz, Material.SHROOMLIGHT);
				}
			}

			for (int dx = -7; dx <= 7; dx += 2) {
				placed += set(cx + dx, floorY + 1, cz - 6, Material.BOOKSHELF);
				placed += set(cx + dx, floorY + 2, cz - 6, Material.BOOKSHELF);
			}

			return placed;
		}

		/** A tower worth climbing to, with a lit top that can be seen from the water. */
		int watchtower(int cx, int cz) {
			int floorY = sea + TERRACE;
			int placed = pad(cx, cz, TOWN_PAD - 3, floorY, Material.COBBLESTONE, Material.STONE);
			int height = 26;

			for (int y = 0; y <= height; y++) {
				int radius = y > height - 4 ? 5 : 4;

				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						double distance = Math.sqrt(dx * dx + dz * dz);

						if (distance > radius + 0.5D) {
							continue;
						}

						if (distance > radius - 1) {
							// The wall, banded so the tower has courses.
							Material band = y % 6 == 0 ? Material.CHISELED_STONE_BRICKS
									: y % 3 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
							placed += set(cx + dx, floorY + y, cz + dz, y > height - 2 ? Material.STONE_BRICKS : band);
							continue;
						}

						placed += set(cx + dx, floorY + y, cz + dz, y == 0 ? Material.POLISHED_ANDESITE
								: y == height ? Material.SEA_LANTERN : Material.AIR);
					}
				}
			}

			placed += doorway(cx, floorY, cz + 4, 1, true);

			// Slit windows up the shaft, on alternating sides.
			for (int y = 5; y < height - 5; y += 5) {
				placed += set(cx + 4, floorY + y, cz, Material.GLASS);
				placed += set(cx - 4, floorY + y + 2, cz, Material.GLASS);
				placed += set(cx, floorY + y, cz + 4, Material.GLASS);
				placed += set(cx, floorY + y + 2, cz - 4, Material.GLASS);
			}

			// A banner-coloured cap, so it is the thing you look for from anywhere on the island.
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > 3) {
						continue;
					}

					placed += set(cx + dx, floorY + height + 1, cz + dz, Material.RED_CONCRETE);
					placed += set(cx + dx, floorY + height + 2, cz + dz,
							Math.abs(dx) + Math.abs(dz) > 1 ? Material.AIR : Material.RED_CONCRETE);
				}
			}

			placed += set(cx, floorY + height + 3, cz, Material.GOLD);
			return placed;
		}

		/** A row of market stalls, each with a striped canopy on posts. */
		int market(int cx, int cz) {
			int floorY = sea + TERRACE;
			int placed = pad(cx, cz, TOWN_PAD, floorY, Material.COBBLESTONE, Material.STONE);
			Material[] cloth = { Material.RED_WOOL, Material.YELLOW_WOOL, Material.BLUE_WOOL,
					Material.WHITE_WOOL, Material.GREEN_WOOL, Material.ORANGE_WOOL };

			for (int stall = 0; stall < 4; stall++) {
				int sx = cx - 5 + (stall % 2) * 10;
				int sz = cz - 5 + (stall / 2) * 10;
				Material canopy = cloth[stall];

				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						boolean post = Math.abs(dx) == 3 && Math.abs(dz) == 2;

						if (post) {
							for (int y = 1; y <= 3; y++) {
								placed += set(sx + dx, floorY + y, sz + dz, Material.SPRUCE_FENCE);
							}
						}

						// The canopy, striped along its length.
						placed += set(sx + dx, floorY + 4, sz + dz,
								Math.abs(dx) % 2 == 0 ? canopy : Material.WHITE_WOOL);
					}
				}

				// The counter, and something stacked behind it.
				for (int dx = -2; dx <= 2; dx++) {
					placed += set(sx + dx, floorY + 1, sz - 2, Material.SPRUCE_PLANKS);
				}

				placed += set(sx - 1, floorY + 1, sz + 1, Material.HAY);
				placed += set(sx + 1, floorY + 1, sz + 1, Material.SPRUCE_LOG);
				placed += set(sx, floorY + 5, sz, Material.OCHRE_FROGLIGHT);
			}

			return placed;
		}

		/**
		 * Hedged beds of colour on the terrace.
		 *
		 * <p>Wool rather than flowers: a flower needs the right block under it and falls off if it
		 * does not get one, and a bed of them planted by a plan that cannot see the world is a bed
		 * of items on the floor by the time anybody arrives.
		 */
		int gardens(int cx, int cz) {
			int floorY = sea + TERRACE;
			int placed = pad(cx, cz, TOWN_PAD, floorY, Material.GRASS, Material.DIRT);
			Material[] beds = { Material.RED_WOOL, Material.YELLOW_WOOL, Material.PURPLE_WOOL, Material.WHITE_WOOL };

			for (int bed = 0; bed < 4; bed++) {
				int bx = cx - 5 + (bed % 2) * 10;
				int bz = cz - 5 + (bed / 2) * 10;

				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;

						if (edge) {
							placed += set(bx + dx, floorY + 1, bz + dz, Material.OAK_LEAVES);
							continue;
						}

						boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
						placed += set(bx + dx, floorY, bz + dz, inner ? beds[bed] : Material.PODZOL);
					}
				}
			}

			// A lantern on a post in the middle of the beds.
			for (int y = 1; y <= 3; y++) {
				placed += set(cx, floorY + y, cz, Material.OAK_FENCE);
			}

			placed += set(cx, floorY + 4, cz, Material.SHROOMLIGHT);
			return placed;
		}

		/**
		 * A stepped ramp from one height to another, wide enough to walk up without thinking.
		 *
		 * <p>Built out of full blocks, one per step, because stairs would have to carry a facing
		 * through the plan and the tests and this is a shape rather than a staircase.
		 */
		int steps(int fromX, int fromZ, int toX, int toZ, int fromY, int toY, int halfWidth, Material tread) {
			int placed = 0;
			double spanX = toX - fromX;
			double spanZ = toZ - fromZ;
			int length = (int) Math.ceil(Math.max(Math.abs(spanX), Math.abs(spanZ)));

			if (length == 0) {
				return 0;
			}

			for (int step = 0; step <= length; step++) {
				double along = (double) step / length;
				int x = fromX + (int) Math.round(spanX * along);
				int z = fromZ + (int) Math.round(spanZ * along);
				int y = fromY + (int) Math.round((toY - fromY) * along);
				boolean acrossX = Math.abs(spanZ) > Math.abs(spanX);

				for (int side = -halfWidth; side <= halfWidth; side++) {
					int px = acrossX ? x + side : x;
					int pz = acrossX ? z : z + side;

					placed += set(px, y, pz, Math.abs(side) == halfWidth ? Material.POLISHED_ANDESITE : tread);

					// Something under the tread wherever the hill has fallen away.
					for (int under = y - 1; under > y - 20; under--) {
						if (materialAt(originX + px, under, originZ + pz).standable()) {
							break;
						}

						placed += set(px, under, pz, Material.STONE_BRICKS);
					}

					for (int over = 1; over <= 4; over++) {
						placed += set(px, y + over, pz, Material.AIR);
					}
				}
			}

			return placed;
		}

		/**
		 * The four ramps down from the plaza to the town.
		 *
		 * <p>Laid after the buildings and the plaza, because both of those flatten and clear
		 * whatever is under them, and a path put down first would be the thing they cleared.
		 */
		int townPaths() {
			int top = sea + SUMMIT;
			int floor = sea + TERRACE;
			int placed = 0;
			int[][] corners = { { -1, 1 }, { 1, 1 }, { 1, -1 }, { -1, -1 } };

			for (int[] corner : corners) {
				// From just inside the plaza rim out to the near edge of the building pad.
				int fromX = corner[0] * (PLAZA_RADIUS - 3);
				int fromZ = corner[1] * (PLAZA_RADIUS - 3);
				int toX = corner[0] * (TOWN_OUT - TOWN_PAD + 2);
				int toZ = corner[1] * (TOWN_OUT - TOWN_PAD + 2);
				placed += steps(fromX, fromZ, toX, toZ, top, floor, 2, Material.STONE_BRICKS);
			}

			return placed;
		}

		/** Lamp posts, so the island is not a dark shape at night. */
		int lamps() {
			int placed = 0;

			for (int index = 0; index < 12; index++) {
				double angle = index * Math.PI / 6.0D;
				int x = (int) Math.round((PLAZA_RADIUS - 1) * Math.cos(angle));
				int z = (int) Math.round((PLAZA_RADIUS - 1) * Math.sin(angle));

				for (int y = 1; y <= 4; y++) {
					placed += set(x, sea + SUMMIT + y, z, Material.OAK_FENCE);
				}

				placed += set(x, sea + SUMMIT + 5, z, Material.SEA_LANTERN);
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
			int floor = sea - 7 + (int) Math.round(7.0D * Math.max(0.0D, Math.min(1.0D, shoal)));
			int placed = 0;
			double patch = Math.sin(dx * 0.17D) * Math.cos(dz * 0.23D);

			for (int y = sea - 11; y <= floor; y++) {
				Material block;

				if (y >= floor - 1) {
					// The lagoon floor: mostly sand, with gravel and clay where it dips.
					block = patch > 0.66D ? Material.GRAVEL : patch < -0.74D ? Material.CLAY : Material.SAND;
				} else {
					block = patch > 0.4D ? Material.TUFF : Material.ANDESITE;
				}

				placed += set(dx, y, dz, block);
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

					for (int y = sea - 10; y <= sea; y++) {
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
				// Above the watchtower, which is the tallest thing here - a balloon level with a
				// roof reads as a balloon that has crashed into it.
				int y = sea + TERRACE + 34 + random.nextInt(10);
				Material skin = colours[count % colours.length];
				int radius = 4;
				balloonCentres.add(new Spot(originX + dx + 0.5D, y + 0.5D, originZ + dz + 0.5D, 0.0F, 0.0F));

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

		/**
		 * In world coordinates.
		 *
		 * <p>A placement this plan has already made at this spot with this same block is dropped:
		 * the world will already have it by the time the second one would be applied, so writing
		 * it twice is a block laid for nothing. Structures level their own footings and clear
		 * their own headroom, and those volumes overlap heavily, so this is most of them.
		 *
		 * <p>What is not dropped is a placement onto a spot the plan has not touched, even one
		 * setting air. Out there the world still holds whatever was there before, and clearing it
		 * is exactly what the command promised to do.
		 */
		private int put(int x, int y, int z, Material material) {
			long spot = key(x, y, z);

			if (current.get(spot) == material) {
				return 0;
			}

			order.add(new Placement(x, y, z, material));
			current.put(spot, material);
			return 1;
		}
	}
}
