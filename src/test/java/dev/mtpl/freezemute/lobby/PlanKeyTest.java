package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.mtpl.freezemute.lobby.LobbyBuilder.Placement;
import dev.mtpl.freezemute.lobby.LobbyBuilder.Plan;

import org.junit.jupiter.api.Test;

/**
 * The plan looks itself up by packed coordinate while it is being built - the lagoon asks what an
 * earlier step left in a column before deciding whether to fill it. Two different blocks sharing a
 * key would make one of them invisible to that question.
 */
class PlanKeyTest {
	@Test
	void noTwoBlocksInAnIslandShareAKey() {
		for (int[] origin : new int[][] { { 0, 0 }, { 1000, -1000 }, { -37, 41 }, { 30_000_000, -30_000_000 } }) {
			Plan plan = LobbyBuilder.plan(origin[0], origin[1], 53);
			Map<String, Long> byCoordinate = new HashMap<>();
			Map<Long, String> byKey = new HashMap<>();

			for (Placement placement : plan.placements()) {
				String coordinate = placement.x() + "," + placement.y() + "," + placement.z();
				long key = pack(placement.x(), placement.y(), placement.z());
				byCoordinate.put(coordinate, key);
				String seen = byKey.putIfAbsent(key, coordinate);

				if (seen != null && !seen.equals(coordinate)) {
					throw new AssertionError(seen + " and " + coordinate + " pack to the same key at origin "
							+ origin[0] + "," + origin[1]);
				}
			}

			Set<Long> keys = new HashSet<>(byCoordinate.values());
			assertEquals(byCoordinate.size(), keys.size(),
					"distinct coordinates should give distinct keys at origin " + origin[0] + "," + origin[1]);
		}
	}

	/** The same packing {@code LobbyBuilder.Site} uses. */
	private static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y + 2048L) & 0xFFFL;
	}
}
