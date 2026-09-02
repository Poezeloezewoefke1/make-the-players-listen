package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.mtpl.freezemute.lobby.LobbyBuilder.Placement;
import dev.mtpl.freezemute.lobby.LobbyBuilder.Plan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The island, checked as geometry rather than trusted as code.
 *
 * <p>Every one of these is something that used to be a guess. Whether the lagoon holds its water,
 * whether the plaza is somewhere you can stand, whether the jetty is over the sea or buried in a
 * hillside - all of it was previously only knowable by building the thing and looking at it.
 */
class IslandPlanTest {
	private static final int ORIGIN_X = 0;
	private static final int ORIGIN_Z = 0;
	private static final int SEA = 53;

	private static Plan plan;
	/** The island as it ends up, after later placements have overwritten earlier ones. */
	private static Map<Long, Material> world;

	@BeforeAll
	static void buildIt() {
		plan = LobbyBuilder.plan(ORIGIN_X, ORIGIN_Z, SEA);
		world = new HashMap<>();

		for (Placement placement : plan.placements()) {
			world.put(key(placement.x(), placement.y(), placement.z()), placement.material());
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) x << 40) ^ ((long) z << 16) ^ y;
	}

	private static Material at(int x, int y, int z) {
		return world.getOrDefault(key(x, y, z), Material.AIR);
	}

	private static Material at(double x, double y, double z) {
		return at((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
	}

	// ------------------------------------------------------------------ water

	@Test
	void theLagoonHoldsItsWater() {
		// Every water block has to be walled in sideways and floored underneath. One gap and the
		// whole lagoon pours off the edge of the island and keeps pouring.
		int checked = 0;

		for (Placement placement : plan.placements()) {
			if (placement.material() != Material.WATER) {
				continue;
			}

			int x = placement.x();
			int y = placement.y();
			int z = placement.z();

			if (at(x, y, z) != Material.WATER) {
				// Overwritten by something later, so it is not water in the end.
				continue;
			}

			checked++;
			assertTrue(at(x, y - 1, z).holdsWater() || at(x, y - 1, z) == Material.WATER,
					"water at " + x + " " + y + " " + z + " has " + at(x, y - 1, z) + " under it");

			int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

			for (int[] side : sides) {
				Material neighbour = at(x + side[0], y, z + side[1]);
				assertTrue(neighbour == Material.WATER || neighbour.holdsWater(),
						"water at " + x + " " + y + " " + z + " leaks " + side[0] + "," + side[1]
								+ " into " + neighbour);
			}
		}

		assertTrue(checked > 500, "expected a lagoon worth checking, found " + checked + " water blocks");
	}

	@Test
	void waterIsNeverInsideTheIsland() {
		// The shore wobbles between about 19 and 33 blocks out, so distance alone says nothing.
		// What has to hold is that no column is both lagoon and island.
		for (Placement placement : plan.placements()) {
			if (placement.material() != Material.WATER) {
				continue;
			}

			for (int y = SEA - 8; y < SEA + 16; y++) {
				Material column = at(placement.x(), y, placement.z());
				assertFalse(column == Material.GRASS || column == Material.DIRT,
						"the column at " + placement.x() + " " + placement.z() + " is both lagoon and island");
			}
		}
	}

	@Test
	void theWaterSurfaceIsWhereItWasAskedFor() {
		// The lagoon, which means water out beyond the plaza. The fountain on top of the hill is
		// also water and is also meant to be there, seventy blocks higher.
		int highest = Integer.MIN_VALUE;

		for (Placement placement : plan.placements()) {
			if (placement.material() != Material.WATER) {
				continue;
			}

			if (Math.hypot(placement.x() - ORIGIN_X, placement.z() - ORIGIN_Z) > 20.0D) {
				highest = Math.max(highest, placement.y());
			}
		}

		assertEquals(SEA, highest, "the lagoon is not level with the height it was asked for");
	}

	@Test
	void theFountainHoldsItsWaterUpOnTheHill() {
		int highest = Integer.MIN_VALUE;
		int blocks = 0;

		for (Placement placement : plan.placements()) {
			if (placement.material() == Material.WATER
					&& Math.hypot(placement.x() - ORIGIN_X, placement.z() - ORIGIN_Z) <= 20.0D) {
				highest = Math.max(highest, placement.y());
				blocks++;
			}
		}

		assertTrue(blocks > 20, "the fountain has " + blocks + " blocks of water in it");
		assertEquals(SEA + LobbyBuilder.summit() + 1, highest,
				"the fountain sits one above the plaza floor, in a basin with a rim");
	}

	// ----------------------------------------------------------- standing room

	@Test
	void thePlazaFloorGoesExactlyWhereTheCommandWasRunFrom() {
		// Which is why /lobby generate has to move whoever ran it: the command asks them to stand
		// where they want the top of the island, and the top of the island is a solid block.
		// The summit is the height the command told them to stand at, and the summit is where the command told them to stand.
		Material atTheirFeet = at(ORIGIN_X, SEA + LobbyBuilder.summit(), ORIGIN_Z);

		assertTrue(atTheirFeet.standable(),
				"a player left standing in " + atTheirFeet + " is a player standing inside a block");
		assertEquals(Material.AIR, at(ORIGIN_X, SEA + LobbyBuilder.summit() + 1, ORIGIN_Z),
				"and the block above it is where they have to end up instead");
	}

	@Test
	void theSpawnIsSomewhereYouCanStand() {
		Spot spawn = plan.spawn();

		assertTrue(at(spawn.x(), spawn.y() - 1, spawn.z()).standable(),
				"nothing to stand on under the spawn");
		assertEquals(Material.AIR, at(spawn.x(), spawn.y(), spawn.z()), "the spawn is inside a block");
		assertEquals(Material.AIR, at(spawn.x(), spawn.y() + 1, spawn.z()), "no headroom at the spawn");
	}

	@Test
	void theQueuePointIsSomewhereYouCanStand() {
		Spot point = plan.queuePoint();

		assertTrue(at(point.x(), point.y() - 1, point.z()).standable(),
				"the pedestal has no top to stand on");
		assertEquals(Material.AIR, at(point.x(), point.y(), point.z()));
		assertEquals(Material.AIR, at(point.x(), point.y() + 1, point.z()));
	}

	/** The yaw somebody at {@code from} needs in order to be looking at {@code to}. */
	private static double yawTowards(Spot from, Spot to) {
		double yaw = Math.toDegrees(-Math.atan2(to.x() - from.x(), to.z() - from.z()));
		return (yaw + 360.0D) % 360.0D;
	}

	private static double yawGap(double a, double b) {
		double gap = Math.abs((a % 360.0D + 360.0D) % 360.0D - (b % 360.0D + 360.0D) % 360.0D);
		return Math.min(gap, 360.0D - gap);
	}

	@Test
	void theSpawnAndTheFigureLookAtEachOther() {
		Spot spawn = plan.spawn();
		Spot point = plan.queuePoint();

		// Somebody arriving should be looking at the thing they are meant to walk up to, and the
		// figure standing on it should not have its back to them. Both were the wrong way round.
		assertTrue(yawGap(spawn.yaw(), yawTowards(spawn, point)) < 30.0D,
				"somebody arriving faces " + spawn.yaw() + " and the pedestal is at "
						+ (int) yawTowards(spawn, point));
		assertTrue(yawGap(point.yaw(), yawTowards(point, spawn)) < 30.0D,
				"the figure faces " + point.yaw() + " and the spawn is at "
						+ (int) yawTowards(point, spawn));
	}

	@Test
	void theQueuePointIsWithinReachOfTheSpawn() {
		Spot spawn = plan.spawn();
		Spot point = plan.queuePoint();
		double walk = Math.hypot(spawn.x() - point.x(), spawn.z() - point.z());

		assertTrue(walk < 16.0D, "the pedestal is " + (int) walk + " blocks from the spawn");
	}

	@Test
	void everyCoursePadCanBeLandedOn() {
		Course course = plan.course();
		assertNotNull(course);

		for (Spot pad : allPads(course)) {
			assertTrue(at(pad.x(), pad.y() - 1, pad.z()).standable(),
					"nothing under the pad at " + pad.describe());
			assertEquals(Material.AIR, at(pad.x(), pad.y(), pad.z()), "the pad at " + pad.describe() + " is filled in");
			assertEquals(Material.AIR, at(pad.x(), pad.y() + 1, pad.z()),
					"no headroom over the pad at " + pad.describe());
		}
	}

	private static java.util.List<Spot> allPads(Course course) {
		java.util.List<Spot> pads = new java.util.ArrayList<>();
		pads.add(course.start());
		pads.addAll(course.checkpoints());
		pads.add(course.finish());
		return pads;
	}

	@Test
	void theCourseTheModBuildsWouldPassTheRulesTheCommandsEnforce() {
		// /lobby course refuses a checkpoint or a finish close enough to the start to be swallowed
		// by it, because standing on the start restarts the run. A generated course that broke its
		// own rule would be one nobody could ever finish.
		Course course = plan.course();

		for (Spot checkpoint : course.checkpoints()) {
			assertFalse(Parkour.swallowedByTheStart(course.start(), checkpoint, 1.5D),
					"the checkpoint at " + checkpoint.describe() + " sits on the start pad");
		}

		assertFalse(Parkour.swallowedByTheStart(course.start(), course.finish(), 1.5D),
				"the finish sits on the start pad, so the course can never be completed");
		assertTrue(course.playable(), "and it has a finish at all");
	}

	@Test
	void everyJumpOnTheCourseIsOneAPlayerCanMake() {
		// Every pad is a single block, so there is nowhere to take a run-up: each jump is made
		// from a standstill. Three blocks middle to middle is the sprint jump that gets you there;
		// four needs the run-up there is nowhere to take, and climbing a block costs about half of
		// the reach.
		//
		// The course used to be laid by sweeping round the ring until the gap first went past the
		// one wanted. The ring is whole blocks, so the gap does not grow smoothly - it goes 2.24,
		// 2.83, 3.16 - and the first value past 2.6 was 3.16. Two of the twenty-four jumps on
		// every course the mod had ever built could not be made.
		List<LobbyBuilder.Step> steps = LobbyBuilder.courseSteps(ORIGIN_X, ORIGIN_Z, SEA + LobbyBuilder.summit() + 2);
		assertTrue(steps.size() > 8, "a course worth measuring");

		for (int index = 1; index < steps.size(); index++) {
			LobbyBuilder.Step from = steps.get(index - 1);
			LobbyBuilder.Step to = steps.get(index);
			double jump = Math.hypot(to.x() - from.x(), to.z() - from.z());
			int climb = to.y() - from.y();
			double furthest = climb > 0 ? LobbyBuilder.furthestClimb() : LobbyBuilder.furthestJump();

			assertTrue(jump <= furthest + 0.001D, "jump " + index + " is " + Math.round(jump * 100) / 100.0
					+ " blocks" + (climb > 0 ? " and climbs " + climb : "") + ", further than the " + furthest
					+ " a player can make from a standstill");
			assertTrue(jump >= 1.5D, "jump " + index + " is only " + jump + " blocks - that is a walk, not a jump");
			assertTrue(climb >= 0, "jump " + index + " goes down, and a course that drops is a course you fall off");
		}
	}

	@Test
	void theCourseClimbsAsOftenAsItSaysItDoes() {
		List<LobbyBuilder.Step> steps = LobbyBuilder.courseSteps(ORIGIN_X, ORIGIN_Z, SEA + LobbyBuilder.summit() + 2);
		int climbed = steps.get(steps.size() - 1).y() - steps.get(0).y();

		assertEquals((steps.size() - 1) / LobbyBuilder.risesEvery(), climbed,
				"the course climbs " + climbed + " blocks over " + (steps.size() - 1) + " jumps");
	}

	@Test
	void theWholeCourseIsInTheAirRatherThanBuriedInTheHill() {
		for (LobbyBuilder.Step step : LobbyBuilder.courseSteps(ORIGIN_X, ORIGIN_Z, SEA + LobbyBuilder.summit() + 2)) {
			assertTrue(at(step.x(), step.y(), step.z()).standable(),
					"the jump at " + step.x() + " " + step.y() + " " + step.z() + " was not placed");
			assertEquals(Material.AIR, at(step.x(), step.y() + 1, step.z()),
					"the jump at " + step.x() + " " + step.y() + " " + step.z() + " has no headroom");
			assertEquals(Material.AIR, at(step.x(), step.y() + 2, step.z()),
					"the jump at " + step.x() + " " + step.y() + " " + step.z() + " is in a tunnel");
		}
	}

	// ------------------------------------------------------------- the town

	/** Where each of the four town buildings stands, on the diagonals. */
	private static int[][] townCentres() {
		int out = LobbyBuilder.townOut();
		return new int[][] { { -out, out }, { out, out }, { out, -out }, { -out, -out } };
	}

	/** The closest the shore ever comes to the middle. */
	private static double nearestShore() {
		double nearest = Double.MAX_VALUE;

		for (int step = 0; step < 3600; step++) {
			nearest = Math.min(nearest, LobbyBuilder.shoreAt(step * Math.PI / 1800.0D));
		}

		return nearest;
	}

	@Test
	void everyTownBuildingStandsOnLandRatherThanOverTheLagoon() {
		// A pad over open water does not float: levelling a footing there fills the lagoon with a
		// pillar of stone down to the seabed. The far edge of every pad has to be inside the
		// closest the shore ever comes, whichever way the wobble happens to fall.
		double shore = nearestShore();

		for (int[] centre : townCentres()) {
			double reach = Math.hypot(centre[0], centre[1]) + LobbyBuilder.townPad();

			assertTrue(reach < shore,
					"a building pad reaches " + (int) reach + " blocks out and the shore can come in to "
							+ (int) shore);
		}
	}

	@Test
	void noTownBuildingSitsOnThePlaza() {
		// Pads clear the sky above them, so one overlapping the plaza would take the plaza floor
		// with it - and the plaza is laid first.
		for (int[] centre : townCentres()) {
			double gap = Math.hypot(centre[0], centre[1]) - LobbyBuilder.townPad();

			assertTrue(gap > LobbyBuilder.plazaRadius() + 1,
					"a building pad comes within " + (int) gap + " blocks of the middle, and the plaza "
							+ "reaches " + (LobbyBuilder.plazaRadius() + 1));
		}
	}

	@Test
	void thereIsSomewhereFlatToStandOnEveryTownPad() {
		int floorY = SEA + LobbyBuilder.terrace();

		for (int[] centre : townCentres()) {
			assertTrue(at(centre[0], floorY, centre[1]).standable(),
					"nothing to stand on in the middle of the pad at " + centre[0] + " " + centre[1]);
			assertEquals(Material.AIR, at(centre[0], floorY + 9, centre[1]),
					"the pad at " + centre[0] + " " + centre[1] + " still has hillside hanging over it");
		}
	}

	@Test
	void theWatchtowerIsTheTallestThingOnTheIsland() {
		int tallest = Integer.MIN_VALUE;
		int tallestX = 0;
		int tallestZ = 0;

		for (Placement placement : plan.placements()) {
			// Balloons float; they are not built on anything.
			if (placement.material().standable() && placement.y() > tallest
					&& placement.y() < SEA + LobbyBuilder.summit() + 30) {
				tallest = placement.y();
				tallestX = placement.x();
				tallestZ = placement.z();
			}
		}

		assertTrue(tallest > SEA + LobbyBuilder.summit(),
				"nothing is built above the plaza, so there is no skyline at all");
		assertTrue(Math.hypot(tallestX, tallestZ) > 20.0D,
				"the tallest thing is at " + tallestX + " " + tallestZ + ", which is not out where the "
						+ "watchtower was put");
	}

	@Test
	void thePathsReachFromThePlazaToTheTown() {
		// Each ramp has to actually cover ground. They came out with zero length once, because the
		// buildings were close enough to the plaza that the two ends met.
		int floorY = SEA + LobbyBuilder.terrace();
		int found = 0;

		for (int[] centre : townCentres()) {
			int x = centre[0] / 2;
			int z = centre[1] / 2;
			boolean solid = false;

			for (int y = floorY; y <= SEA + LobbyBuilder.summit(); y++) {
				if (at(x, y, z).standable()) {
					solid = true;
					break;
				}
			}

			if (solid) {
				found++;
			}
		}

		assertEquals(4, found, "there is no way to walk between the plaza and the town on every side");
	}

	@Test
	void theCliffsAreCutOutOfMoreThanOneKindOfRock() {
		// A twenty block cliff of plain stone is a wall. This is what makes it read as a cliff.
		Set<Material> rock = new HashSet<>();

		for (Placement placement : plan.placements()) {
			switch (placement.material()) {
				case STONE, ANDESITE, GRANITE, DIORITE, TUFF, DEEPSLATE -> rock.add(placement.material());
				default -> {
				}
			}
		}

		assertTrue(rock.size() >= 5, "the ground is made of " + rock + ", which is not much of a cliff");
	}

	@Test
	void theIslandIsBuiltOutOfAWholePalette() {
		Set<Material> used = new HashSet<>();

		for (Placement placement : plan.placements()) {
			used.add(placement.material());
		}

		assertTrue(used.size() > 45,
				"the island uses " + used.size() + " kinds of block, which is a lot of grey");
	}

	// ---------------------------------------------------------------- the built

	@Test
	void theJettyHasAWalkableLaneDownTheMiddle() {
		// The edges carry railings and lamp posts on purpose. The middle is the bit you walk on,
		// and it has to be clear the whole way.
		int deck = SEA + 2;
		int lane = 0;

		for (Placement placement : plan.placements()) {
			if (placement.material() != Material.OAK_PLANKS || placement.y() != deck
					|| placement.x() != ORIGIN_X) {
				continue;
			}

			if (at(placement.x(), deck, placement.z()) != Material.OAK_PLANKS) {
				continue;
			}

			lane++;
			assertEquals(Material.AIR, at(placement.x(), deck + 1, placement.z()),
					"the middle of the jetty is blocked at z " + placement.z());
			assertEquals(Material.AIR, at(placement.x(), deck + 2, placement.z()),
					"no headroom on the jetty at z " + placement.z());
		}

		assertTrue(lane > 15, "expected a jetty to walk down, found " + lane + " blocks of lane");
	}

	@Test
	void theJettyHasRailingsOnBothSides() {
		int deck = SEA + 2;
		boolean left = false;
		boolean right = false;

		for (Placement placement : plan.placements()) {
			if (placement.material() != Material.OAK_FENCE || placement.y() != deck + 1) {
				continue;
			}

			left |= placement.x() == ORIGIN_X - 1;
			right |= placement.x() == ORIGIN_X + 1;
		}

		assertTrue(left && right, "the jetty should have a rail on each side");
	}

	@Test
	void theJettyReachesOutOverTheSeaRatherThanEndingOnTheBeach() {
		int deck = SEA + 2;
		double furthest = 0.0D;

		for (Placement placement : plan.placements()) {
			if (placement.material() == Material.OAK_PLANKS && placement.y() == deck) {
				furthest = Math.max(furthest, Math.hypot(placement.x() - ORIGIN_X, placement.z() - ORIGIN_Z));
			}
		}

		assertTrue(furthest > 30.0D, "the jetty only reaches " + (int) furthest + " blocks out");
	}

	@Test
	void theBalloonsFloatWellClearOfTheGround() {
		// Asked of the plan rather than inferred from the blocks: the canopies over the market and
		// the beds in the gardens are wool too, so counting wool would be measuring a market stall.
		assertEquals(4, plan.balloons().size());

		for (Spot balloon : plan.balloons()) {
			assertTrue(balloon.y() > SEA + LobbyBuilder.summit() + 20,
					"a balloon at " + balloon.describe() + " is down among the buildings");

			// Below its own basket, which hangs about eight blocks under the middle on ropes,
			// there has to be a good drop of nothing - otherwise it is parked on a roof.
			for (int drop = 10; drop <= 24; drop++) {
				assertFalse(at(balloon.x(), balloon.y() - drop, balloon.z()).standable(),
						"the balloon at " + balloon.describe() + " is resting on something "
								+ drop + " blocks below it");
			}
		}
	}

	@Test
	void theLighthouseAndTheShelterBothGotBuilt() {
		// Both sit at fixed spots and both quietly give up if the shore has moved out from under
		// them, so a change to the coastline could delete either one without a word.
		int lighthouse = 0;
		int shelter = 0;

		for (Placement placement : plan.placements()) {
			switch (placement.material()) {
				case WHITE_CONCRETE -> lighthouse++;
				case RED_CONCRETE -> shelter++;
				default -> {
				}
			}
		}

		assertTrue(lighthouse > 40, "the lighthouse is missing or tiny: " + lighthouse + " blocks");
		assertTrue(shelter > 20, "the shelter roof is missing or tiny: " + shelter + " blocks");
	}

	@Test
	void thereArePalmsAndTheirLeavesWillNotDecay() {
		// Minecraft works a leaf's distance out by stepping from log to leaf to leaf through
		// blocks that touch face to face, and anything more than six steps from a log rots. A
		// leaf that only touches the tree diagonally is not connected to it at all, so measuring
		// straight line distance would say a frond is fine right up until it falls off.
		Set<Long> logs = new HashSet<>();
		Set<Long> leaves = new HashSet<>();

		for (Placement placement : plan.placements()) {
			if (placement.material() == Material.JUNGLE_LOG) {
				logs.add(key(placement.x(), placement.y(), placement.z()));
			} else if (placement.material() == Material.JUNGLE_LEAVES) {
				leaves.add(key(placement.x(), placement.y(), placement.z()));
			}
		}

		assertTrue(logs.size() > 40, "expected palm trunks, found " + logs.size() + " logs");
		assertTrue(leaves.size() > 100, "expected fronds, found " + leaves.size() + " leaves");

		Map<Long, Integer> distance = new HashMap<>();
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

		for (Placement placement : plan.placements()) {
			if (placement.material() == Material.JUNGLE_LOG) {
				queue.add(new int[] { placement.x(), placement.y(), placement.z(), 0 });
			}
		}

		int[][] faces = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

		while (!queue.isEmpty()) {
			int[] here = queue.poll();

			for (int[] face : faces) {
				int x = here[0] + face[0];
				int y = here[1] + face[1];
				int z = here[2] + face[2];
				long neighbour = key(x, y, z);
				int step = here[3] + 1;

				if (!leaves.contains(neighbour) || step > 6) {
					continue;
				}

				Integer known = distance.get(neighbour);

				if (known == null || known > step) {
					distance.put(neighbour, step);
					queue.add(new int[] { x, y, z, step });
				}
			}
		}

		for (Placement placement : plan.placements()) {
			if (placement.material() != Material.JUNGLE_LEAVES) {
				continue;
			}

			assertTrue(distance.containsKey(key(placement.x(), placement.y(), placement.z())),
					"the leaf at " + placement.x() + " " + placement.y() + " " + placement.z()
							+ " is not joined to any trunk, so it would rot away");
		}
	}

	@Test
	void nothingIsBuiltOutsideTheWorld() {
		for (Placement placement : plan.placements()) {
			assertTrue(placement.y() >= -64, "block at y " + placement.y() + " is below the world");
			assertTrue(placement.y() < 320, "block at y " + placement.y() + " is above the world");
		}
	}

	@Test
	void theIslandStaysWithinTheAreaTheCommandPromises() {
		for (Placement placement : plan.placements()) {
			double distance = Math.hypot(placement.x() - ORIGIN_X, placement.z() - ORIGIN_Z);
			assertTrue(distance <= LobbyBuilder.reach() + 4.0D,
					"a block " + (int) distance + " blocks out is further than the command warns about");
		}
	}

	@Test
	void theSameSpotAlwaysGivesTheSameIsland() {
		Plan again = LobbyBuilder.plan(ORIGIN_X, ORIGIN_Z, SEA);
		assertEquals(plan.placements(), again.placements());
		assertEquals(plan.spawn(), again.spawn());
	}

	@Test
	void aDifferentSpotGivesADifferentIsland() {
		Plan elsewhere = LobbyBuilder.plan(500, -300, SEA);
		Set<Material> here = new HashSet<>();
		Set<Material> there = new HashSet<>();

		for (Placement placement : plan.placements()) {
			here.add(placement.material());
		}

		for (Placement placement : elsewhere.placements()) {
			there.add(placement.material());
		}

		assertEquals(here, there, "both islands should be made of the same kinds of block");
		assertTrue(elsewhere.placements().size() > 1000);
	}

	@Test
	void theGroundRisesInSteps() {
		// Tiers with cliffs between them is the point of the shape; a smooth cone is not.
		//
		// Swept round rather than sighted along one line. The lines straight out from the middle
		// are where the stairs down to the sea go, and a stair is deliberately the one thing on
		// the island that is not a terrace - reading the shape off one of those said the island
		// had been flattened when all that had happened was that it had been given a way down.
		Set<Integer> surfaces = new HashSet<>();

		for (int turn = 0; turn < 24; turn++) {
			double angle = turn * Math.PI / 12.0D;

			for (int distance = 0; distance < LobbyBuilder.reach(); distance++) {
				int x = ORIGIN_X + (int) Math.round(distance * Math.cos(angle));
				int z = ORIGIN_Z + (int) Math.round(distance * Math.sin(angle));

				for (int y = SEA + 20; y > SEA - 10; y--) {
					Material material = at(x, y, z);

					if (material == Material.GRASS || material == Material.SAND) {
						surfaces.add(y - SEA);
						break;
					}
				}
			}
		}

		assertTrue(surfaces.size() >= 5, "expected several ground levels, found " + surfaces);
	}
	@Test
	void nothingBuiltLaterEatsSomethingBuiltEarlier() {
		// The order things are laid in is load bearing, and everything that levels its own footing
		// also clears the sky above it. A new structure whose footprint overlaps an old one takes
		// pieces out of it and says nothing: a lamp post with its middle missing, a tower with no
		// light on top. The three that have actually been damaged by something added later, and
		// the one that would be worst to lose, are checked by name.
		for (int index = 0; index < 12; index++) {
			double angle = index * Math.PI / 6.0D;
			int x = ORIGIN_X + (int) Math.round((LobbyBuilder.plazaRadius() - 1) * Math.cos(angle));
			int z = ORIGIN_Z + (int) Math.round((LobbyBuilder.plazaRadius() - 1) * Math.sin(angle));

			for (int up = 1; up <= 4; up++) {
				assertEquals(Material.OAK_FENCE, at(x, SEA + LobbyBuilder.summit() + up, z),
						"the lamp post at " + x + " " + z + " is missing its post " + up + " up");
			}

			assertEquals(Material.SEA_LANTERN, at(x, SEA + LobbyBuilder.summit() + 5, z),
					"the lamp post at " + x + " " + z + " has lost its light");
		}

		int tower = SEA + LobbyBuilder.terrace();
		assertEquals(Material.SEA_LANTERN, at(LobbyBuilder.townOut(), tower + 26, -LobbyBuilder.townOut()),
				"the watchtower has lost the light at the top of its shaft");
		assertEquals(Material.GOLD, at(LobbyBuilder.townOut(), tower + 29, -LobbyBuilder.townOut()),
				"the watchtower has lost the gold off its cap");

		Spot point = plan.queuePoint();
		assertTrue(at(point.x(), point.y() - 1, point.z()).standable(),
				"the pedestal the figure stands on has been taken out from under it");
	}

	// ------------------------------------------------------------ getting about

	/**
	 * Everywhere a player could walk to from a starting spot, as standing positions.
	 *
	 * <p>This is the only honest way to ask whether a stair works. Measuring treads says the
	 * blocks are where the arithmetic put them; it does not say a person can get up them. A
	 * staircase whose treads are each one across and one up is still unclimbable if the floor
	 * above it is a ceiling over the last of them, and that is exactly the bug this was written
	 * for.
	 *
	 * <p>The rules are vanilla's, kept deliberately mean: you stand where there is something
	 * solid under your feet and two clear blocks for your body, you move one block at a time to a
	 * side, you can rise one block or drop three, and rising needs the block over your own head
	 * clear as well - stepping up carries you through it.
	 */
	private static Set<Long> walkableFrom(int x, int y, int z, int span) {
		Set<Long> seen = new HashSet<>();
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

		if (!canStand(x, y, z)) {
			return seen;
		}

		seen.add(key(x, y, z));
		queue.add(new int[] { x, y, z });

		while (!queue.isEmpty()) {
			for (int[] there : stepsFrom(queue.poll(), span)) {
				if (seen.add(key(there[0], there[1], there[2]))) {
					queue.add(there);
				}
			}
		}

		return seen;
	}

	/** Everywhere one step from here. The rules live in one place so both walks obey the same ones. */
	private static List<int[]> stepsFrom(int[] here, int span) {
		List<int[]> onwards = new java.util.ArrayList<>(4);
		int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

		for (int[] side : sides) {
			int nx = here[0] + side[0];
			int nz = here[2] + side[1];

			if (Math.abs(nx - ORIGIN_X) > span || Math.abs(nz - ORIGIN_Z) > span) {
				continue;
			}

			for (int drop = 1; drop >= -3; drop--) {
				int ny = here[1] + drop;

				if (drop == 1 && at(here[0], here[1] + 2, here[2]).standable()) {
					continue;
				}

				if (!canStand(nx, ny, nz)) {
					continue;
				}

				onwards.add(new int[] { nx, ny, nz });
				break;
			}
		}

		return onwards;
	}

	private static boolean canStand(int x, int y, int z) {
		return at(x, y - 1, z).standable() && !at(x, y, z).standable() && !at(x, y + 1, z).standable();
	}

	/** Everywhere reachable from the spawn. Worked out once - the walk is the same every time. */
	private static Set<Long> fromTheSpawn;

	private static Set<Long> fromTheSpawn() {
		if (fromTheSpawn == null) {
			Spot spawn = plan.spawn();
			fromTheSpawn = walkableFrom((int) Math.floor(spawn.x()), (int) Math.floor(spawn.y()),
					(int) Math.floor(spawn.z()), LobbyBuilder.reach());
		}

		return fromTheSpawn;
	}

	/** Whether somebody could get to within a block of the given standing spot. */
	private static boolean canGetTo(double x, double y, double z) {
		int fx = (int) Math.floor(x);
		int fy = (int) Math.floor(y);
		int fz = (int) Math.floor(z);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (fromTheSpawn().contains(key(fx + dx, fy + dy, fz + dz))) {
						return true;
					}
				}
			}
		}

		return false;
	}

	@Test
	void theIslandIsOnePlaceRatherThanSeveral() {
		assertTrue(fromTheSpawn().size() > 5_000,
				"only " + fromTheSpawn().size() + " places are walkable from the spawn - the island is in pieces");
	}

	@Test
	void everythingWorthWalkingToCanBeWalkedTo() {
		int out = LobbyBuilder.townOut();
		int terrace = SEA + LobbyBuilder.terrace() + 1;

		assertTrue(canGetTo(-out, terrace, out), "nobody can walk to the great hall");
		assertTrue(canGetTo(out, terrace, out), "nobody can walk to the market");
		assertTrue(canGetTo(out, terrace, -out + 4), "nobody can walk to the watchtower door");
		assertTrue(canGetTo(-out, terrace, -out), "nobody can walk to the gardens");

		Spot point = plan.queuePoint();
		assertTrue(canGetTo(point.x(), point.y(), point.z()), "nobody can walk to the queue point");
	}

	@Test
	void theCourseCanBeStartedByWalkingOntoIt() {
		// The start was a gold block four above the hillside with nothing beside it. Everything
		// else about the course was right - the jumps, the checkpoints, the rules it obeys - and
		// none of it mattered, because the only way onto it was a command.
		Spot start = plan.course().start();

		assertTrue(canGetTo(start.x(), start.y(), start.z()),
				"nobody can walk from the spawn to the start of the course at " + start.describe());
	}

	@Test
	void anywhereYouCanWalkToIsSomewhereYouCanWalkBackFrom() {
		// The island is terraced, and a terrace is a cliff seen from underneath. Before there were
		// stairs down it, seventeen thousand of the nineteen thousand places a player could stand
		// were places they could reach by walking off an edge and could never leave again - the
		// beach, the jetty, the whole lower half of the island. Nothing about the plan looked
		// wrong. You only find it by walking it and then trying to walk back.
		Spot spawn = plan.spawn();
		int[] start = { (int) Math.floor(spawn.x()), (int) Math.floor(spawn.y()), (int) Math.floor(spawn.z()) };
		Map<Long, List<Long>> backwards = new HashMap<>();
		Map<Long, int[]> places = new HashMap<>();
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		places.put(key(start[0], start[1], start[2]), start);
		queue.add(start);

		while (!queue.isEmpty()) {
			int[] here = queue.poll();

			for (int[] there : stepsFrom(here, LobbyBuilder.reach())) {
				long onwards = key(there[0], there[1], there[2]);
				backwards.computeIfAbsent(onwards, unused -> new java.util.ArrayList<>())
						.add(key(here[0], here[1], here[2]));

				if (places.putIfAbsent(onwards, there) == null) {
					queue.add(there);
				}
			}
		}

		// Now the same walk with every step taken the other way, from the spawn outwards.
		Set<Long> home = new HashSet<>();
		java.util.ArrayDeque<Long> returning = new java.util.ArrayDeque<>();
		home.add(key(start[0], start[1], start[2]));
		returning.add(key(start[0], start[1], start[2]));

		while (!returning.isEmpty()) {
			for (long previous : backwards.getOrDefault(returning.poll(), List.of())) {
				if (home.add(previous)) {
					returning.add(previous);
				}
			}
		}

		List<int[]> stranded = new java.util.ArrayList<>();

		for (Map.Entry<Long, int[]> place : places.entrySet()) {
			// This walk cannot swim, and in Minecraft you can always swim up. Anywhere touching
			// water is somewhere the walk is wrong about, not somewhere a player is stuck.
			if (!home.contains(place.getKey()) && !nearWater(place.getValue())) {
				stranded.add(place.getValue());
			}
		}

		assertTrue(places.size() > 5_000, "only " + places.size() + " places to walk - the island is in pieces");
		assertTrue(stranded.size() < places.size() / 100,
				stranded.size() + " of " + places.size() + " places on dry land cannot be walked back from, "
						+ "the first at " + describe(stranded));
	}

	private static String describe(List<int[]> places) {
		return places.isEmpty() ? "nowhere" : places.get(0)[0] + " " + places.get(0)[1] + " " + places.get(0)[2];
	}

	private static boolean nearWater(int[] place) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (at(place[0] + dx, place[1] + dy, place[2] + dz) == Material.WATER) {
						return true;
					}
				}
			}
		}

		return false;
	}

	@Test
	void theTowerCanBeClimbedAllTheWayUpFromTheSpawn() {
		// The tower had a door, a shaft and a light on top, and no way between them: a sealed tube
		// dressed as somewhere to go. Walk it.
		int lookout = SEA + LobbyBuilder.terrace() + LobbyBuilder.towerLookout();
		int onTheLookout = 0;

		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (fromTheSpawn().contains(key(LobbyBuilder.townOut() + dx, lookout + 1,
						-LobbyBuilder.townOut() + dz))) {
					onTheLookout++;
				}
			}
		}

		assertTrue(onTheLookout > 0, "nobody can walk from the spawn up the tower to the lookout at y " + lookout);
	}

	@Test
	void noTreadOfTheTowerStairHasACeilingOnIt() {
		// One block of headroom is not enough to walk under, and the whole point of a stair is
		// that you walk up it. This is what the arithmetic version of the spiral kept getting
		// wrong: two treads rounding into the same column put one directly over the other.
		int cx = LobbyBuilder.townOut();
		int cz = -LobbyBuilder.townOut();
		int floorY = SEA + LobbyBuilder.terrace();
		int treads = 0;

		for (int step = 1; step < LobbyBuilder.towerLookout(); step++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (Math.sqrt(dx * dx + dz * dz) > 3.0D || !at(cx + dx, floorY + step, cz + dz).standable()) {
						continue;
					}

					treads++;
					assertFalse(at(cx + dx, floorY + step + 1, cz + dz).standable(),
							"the tread at " + dx + "," + (floorY + step) + "," + dz + " has a block on top of it");
					assertFalse(at(cx + dx, floorY + step + 2, cz + dz).standable(),
							"the tread at " + dx + "," + (floorY + step) + "," + dz + " has a block two above it");
				}
			}
		}

		assertEquals(LobbyBuilder.towerLookout() - 1, treads,
				"expected one tread per level of the shaft, found " + treads);
	}

	@Test
	void everyTreadIsOneStepFromTheOneBelowIt() {
		int cx = LobbyBuilder.townOut();
		int cz = -LobbyBuilder.townOut();
		int floorY = SEA + LobbyBuilder.terrace();
		int[] last = null;

		for (int step = 1; step < LobbyBuilder.towerLookout(); step++) {
			int[] here = null;

			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (Math.sqrt(dx * dx + dz * dz) > 3.0D || !at(cx + dx, floorY + step, cz + dz).standable()) {
						continue;
					}

					here = new int[] { dx, dz };
				}
			}

			assertNotNull(here, "the shaft has no tread at level " + step);

			if (last != null) {
				int walk = Math.abs(here[0] - last[0]) + Math.abs(here[1] - last[1]);
				assertEquals(1, walk, "the tread at level " + step + " is " + walk + " blocks from the one below it");
			}

			last = here;
		}
	}
}
