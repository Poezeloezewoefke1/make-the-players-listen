package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How far somebody falls in the lobby before the floor is put back under them. */
class VoidCatchTest {
	@Test
	void aFallEndsAboutTwentyBlocksDownRatherThanAtTheConfiguredFloor() {
		// An island with its water at 53 puts the spawn at 65. Falling to the default -5 would be
		// four seconds of nothing.
		assertEquals(41.0D, Parkour.catchLevel(-5.0D, 65.0D));
	}

	@Test
	void aConfiguredFloorAboveThatOneWins() {
		// Somewhere with a deliberate floor to catch people on, higher than the usual drop.
		assertEquals(50.0D, Parkour.catchLevel(50.0D, 65.0D));
	}

	@Test
	void theCatchIsNeverAtOrAboveTheFloorPeopleStandOn() {
		// Otherwise everybody in the room counts as falling, every tick, forever.
		for (double configured : new double[] { 64.0D, 65.0D, 100.0D, 5000.0D }) {
			double level = Parkour.catchLevel(configured, 65.0D);
			assertTrue(level < 65.0D, "a catch at " + level + " is not below a spawn at 65");
			assertTrue(level <= 61.0D, "a catch at " + level + " is too close under the spawn");
		}
	}

	@Test
	void somebodyFlyingIsNotFalling() {
		double level = Parkour.catchLevel(-5.0D, 65.0D);

		// Staff building underneath the island. Catching them would drag them back to the spawn
		// twenty times a second for as long as they worked down there.
		assertFalse(Parkour.caught(true, level - 100.0D, level), "flying is not falling");
		assertTrue(Parkour.caught(false, level - 1.0D, level), "and a runner who missed a jump is");
	}

	@Test
	void standingOnTheIslandIsNeverCaught() {
		double level = Parkour.catchLevel(-5.0D, 65.0D);

		assertFalse(Parkour.caught(false, 65.0D, level));
		assertFalse(Parkour.caught(false, level, level), "exactly at the line is not through it");
	}

	@Test
	void aLobbyDownAtTheBottomOfTheWorldStillGetsACatch() {
		assertEquals(-24.0D, Parkour.catchLevel(-64.0D, 0.0D));
		assertTrue(Parkour.catchLevel(-5.0D, -40.0D) < -40.0D);
	}
}
