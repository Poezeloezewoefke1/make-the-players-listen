package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
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
		// Three tiers with cliffs between them is the point of the shape; a smooth cone is not.
		Set<Integer> surfaces = new HashSet<>();

		for (int distance = 0; distance < 26; distance++) {
			Material found = null;
			int top = Integer.MIN_VALUE;

			for (int y = SEA + 20; y > SEA - 10; y--) {
				Material material = at(ORIGIN_X + distance, y, ORIGIN_Z);

				if (material == Material.GRASS || material == Material.SAND) {
					found = material;
					top = y;
					break;
				}
			}

			if (found != null) {
				surfaces.add(top - SEA);
			}
		}

		assertTrue(surfaces.size() >= 3, "expected several ground levels, found " + surfaces);
	}
}
