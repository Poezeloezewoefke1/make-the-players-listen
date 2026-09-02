package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.mtpl.freezemute.lobby.LobbyBuilder.Placement;
import dev.mtpl.freezemute.lobby.LobbyBuilder.Plan;

import org.junit.jupiter.api.Test;

/**
 * The island, built where somebody actually stands rather than at nought nought.
 *
 * <p>Everything else that checks the shape of the island checks one built at the origin, at a sea
 * level of 53. Nobody's lobby is there. The command takes the spot from wherever the staff member
 * happens to be, so the terrain function, the town, the stairs and the water are all being asked
 * questions about coordinates in the millions, sea levels below zero, and a random seed taken from
 * the spot itself - and none of that was ever exercised.
 */
class IslandAnywhereTest {
	/** Spots a real lobby might be built at: near home, far out, high, and below sea level. */
	private static final int[][] SPOTS = {
			{ 1_234, -5_678, 53 },
			{ -40, 90, 120 },
			{ 3_000_000, -2_000_000, 53 },
			{ 7, 7, -20 },
	};

	private record Island(Plan plan, Map<Long, Material> blocks, int originX, int originZ, int sea) {
		Material at(int x, int y, int z) {
			return blocks.getOrDefault(key(x, y, z), Material.AIR);
		}

		boolean canStand(int x, int y, int z) {
			return at(x, y - 1, z).standable() && !at(x, y, z).standable() && !at(x, y + 1, z).standable();
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) x << 40) ^ ((long) z << 16) ^ y;
	}

	private static Island lay(int originX, int originZ, int sea) {
		Plan plan = LobbyBuilder.plan(originX, originZ, sea);
		Map<Long, Material> blocks = new HashMap<>(600_000);

		for (Placement placement : plan.placements()) {
			blocks.put(key(placement.x(), placement.y(), placement.z()), placement.material());
		}

		return new Island(plan, blocks, originX, originZ, sea);
	}

	private static Set<Long> walkFromTheSpawn(Island island) {
		Spot spawn = island.plan().spawn();
		int x = (int) Math.floor(spawn.x());
		int y = (int) Math.floor(spawn.y());
		int z = (int) Math.floor(spawn.z());
		Set<Long> seen = new HashSet<>();
		Deque<int[]> queue = new ArrayDeque<>();

		if (!island.canStand(x, y, z)) {
			return seen;
		}

		seen.add(key(x, y, z));
		queue.add(new int[] { x, y, z });
		int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		int span = LobbyBuilder.reach();

		while (!queue.isEmpty()) {
			int[] here = queue.poll();

			for (int[] side : sides) {
				int nx = here[0] + side[0];
				int nz = here[2] + side[1];

				if (Math.abs(nx - island.originX()) > span || Math.abs(nz - island.originZ()) > span) {
					continue;
				}

				for (int drop = 1; drop >= -3; drop--) {
					int ny = here[1] + drop;

					if (drop == 1 && island.at(here[0], here[1] + 2, here[2]).standable()) {
						continue;
					}

					if (!island.canStand(nx, ny, nz)) {
						continue;
					}

					if (seen.add(key(nx, ny, nz))) {
						queue.add(new int[] { nx, ny, nz });
					}

					break;
				}
			}
		}

		return seen;
	}

	@Test
	void theLagoonHoldsItsWaterWhereverTheIslandIsBuilt() {
		for (int[] spot : SPOTS) {
			Island island = lay(spot[0], spot[1], spot[2]);
			int water = 0;
			int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

			for (Placement placement : island.plan().placements()) {
				int x = placement.x();
				int y = placement.y();
				int z = placement.z();

				if (island.at(x, y, z) != Material.WATER) {
					continue;
				}

				water++;
				Material under = island.at(x, y - 1, z);
				assertTrue(under.holdsWater() || under == Material.WATER,
						"water at " + x + " " + y + " " + z + " on an island at " + spot[0] + " " + spot[1]
								+ " has " + under + " under it, so the lagoon pours out of the bottom");

				for (int[] side : sides) {
					Material beside = island.at(x + side[0], y, z + side[1]);
					assertTrue(beside == Material.WATER || beside.holdsWater(),
							"water at " + x + " " + y + " " + z + " on an island at " + spot[0] + " " + spot[1]
									+ " has " + beside + " beside it");
				}
			}

			assertTrue(water > 5_000, "an island at " + spot[0] + " " + spot[1] + " has only " + water
					+ " blocks of lagoon");
		}
	}

	@Test
	void everythingIsStillWalkableWhereverTheIslandIsBuilt() {
		for (int[] spot : SPOTS) {
			Island island = lay(spot[0], spot[1], spot[2]);
			Set<Long> reached = walkFromTheSpawn(island);
			String where = "an island at " + spot[0] + " " + spot[1] + " with its water at " + spot[2];

			assertTrue(reached.size() > 5_000,
					where + " has only " + reached.size() + " places to walk - it is in pieces");

			int lookout = spot[2] + LobbyBuilder.terrace() + LobbyBuilder.towerLookout();
			boolean climbed = false;

			for (int dx = -4; dx <= 4 && !climbed; dx++) {
				for (int dz = -4; dz <= 4 && !climbed; dz++) {
					climbed = reached.contains(key(spot[0] + LobbyBuilder.townOut() + dx, lookout + 1,
							spot[1] - LobbyBuilder.townOut() + dz));
				}
			}

			assertTrue(climbed, "nobody can climb the tower on " + where);

			Spot start = island.plan().course().start();
			boolean onTheCourse = false;

			for (int dx = -1; dx <= 1 && !onTheCourse; dx++) {
				for (int dy = -1; dy <= 1 && !onTheCourse; dy++) {
					for (int dz = -1; dz <= 1 && !onTheCourse; dz++) {
						onTheCourse = reached.contains(key((int) Math.floor(start.x()) + dx,
								(int) Math.floor(start.y()) + dy, (int) Math.floor(start.z()) + dz));
					}
				}
			}

			assertTrue(onTheCourse, "nobody can walk onto the course on " + where);
		}
	}

	@Test
	void theIslandIsTheSameShapeWhereverItIsBuilt() {
		// Only the trimmings are seeded from the spot - the palms, the rocks, the balloons. The
		// ground, the town and the water are the same everywhere, and a change that made the shape
		// itself depend on where you stand would mean none of the other tests said anything about
		// anybody's actual lobby.
		Island home = lay(0, 0, 53);
		int homeWater = count(home, Material.WATER);

		for (int[] spot : SPOTS) {
			Island island = lay(spot[0], spot[1], spot[2]);
			assertEquals(homeWater, count(island, Material.WATER),
					"an island at " + spot[0] + " " + spot[1] + " has a different lagoon from one at the origin");
		}
	}

	private static int count(Island island, Material material) {
		int found = 0;

		for (Material block : island.blocks().values()) {
			if (block == material) {
				found++;
			}
		}

		return found;
	}
	@Test
	void whatTheBuilderSaysItReachesIsWhatItReaches() {
		// The command refuses a spot too near the top or the bottom of the room, and it has to do
		// that before anything is planned, so the reach is written down rather than worked out.
		// This is what keeps the written number honest: Minecraft drops a block placed outside the
		// world without a word, so an island built past the edge comes out with pieces missing.
		for (int sea : new int[] { -40, 0, 53, 100, 240 }) {
			Plan plan = LobbyBuilder.plan(sea * 7, sea * 3, sea);
			int spawn = (int) Math.floor(plan.spawn().y());
			int lowest = Integer.MAX_VALUE;
			int highest = Integer.MIN_VALUE;

			for (Placement placement : plan.placements()) {
				lowest = Math.min(lowest, placement.y());
				highest = Math.max(highest, placement.y());
			}

			assertTrue(spawn - lowest <= LobbyBuilder.digsDown(), "an island with its water at " + sea
					+ " digs " + (spawn - lowest) + " below the spawn, and the builder promises "
					+ LobbyBuilder.digsDown());
			assertTrue(highest - spawn <= LobbyBuilder.reachesUp(), "an island with its water at " + sea
					+ " reaches " + (highest - spawn) + " above the spawn, and the builder promises "
					+ LobbyBuilder.reachesUp());
		}
	}

	@Test
	void theRoomIsAsTallAsTheDataPackSaysItIs() {
		// Two numbers describing one thing, in two files. The builder needs them as numbers to
		// know whether an island fits; the pack needs them as JSON to build the dimension at all.
		String pack = LobbyDimension.DIMENSION_TYPE.replaceAll("\\s+", "");

		assertTrue(pack.contains("\"min_y\":" + LobbyDimension.BOTTOM_Y),
				"the pack does not put the floor at " + LobbyDimension.BOTTOM_Y + ": " + pack);
		assertTrue(pack.contains("\"height\":" + (LobbyDimension.TOP_Y - LobbyDimension.BOTTOM_Y)),
				"the pack is not " + (LobbyDimension.TOP_Y - LobbyDimension.BOTTOM_Y) + " tall: " + pack);
	}
}
