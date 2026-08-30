package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class SpotTest {
	/**
	 * A German host renders {@code %.1f} as {@code 65,0}. Coordinates somebody is meant to read off
	 * the screen and type back into /tp are not the place for local number conventions, and a
	 * leaderboard should be the same shape wherever the server happens to be plugged in.
	 */
	@Test
	void numbersLookTheSameWhereverTheServerIs() {
		Locale original = Locale.getDefault();

		try {
			Locale.setDefault(Locale.GERMANY);

			assertEquals("10.5 65.0 -3.2", new Spot(10.5D, 65.0D, -3.24D, 0.0F, 0.0F).describe());
			assertEquals("1:02.35", CourseRecord.format(62_350L));
			assertEquals("9.07", CourseRecord.format(9_070L));
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void distanceIsSquaredSoTheHotPathNeverTakesARoot() {
		Spot spot = new Spot(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);

		assertEquals(0.0D, spot.distanceSquared(0.0D, 0.0D, 0.0D));
		assertEquals(9.0D, spot.distanceSquared(3.0D, 0.0D, 0.0D));
		assertEquals(14.0D, spot.distanceSquared(1.0D, 2.0D, 3.0D));
	}

	@Test
	void aSpotSurvivesBeingWrittenOutAndReadBack() {
		Spot spot = new Spot(1.5D, 64.0D, -2.5D, 180.0F, -30.0F);
		Spot again = Spot.fromJson(spot.toJson(), null);

		assertEquals(spot, again);
	}

	@Test
	void aMissingOrBrokenSpotFallsBackRatherThanBlowingUp() {
		Spot fallback = new Spot(0.5D, 65.0D, 0.5D, 0.0F, 0.0F);

		assertSame(fallback, Spot.fromJson(null, fallback));
		assertSame(fallback, Spot.fromJson(new com.google.gson.JsonObject(), fallback));
	}

	@Test
	void anglesDefaultToZeroWhenTheyWereNeverWritten() {
		com.google.gson.JsonObject object = new com.google.gson.JsonObject();
		object.addProperty("x", 1.0D);
		object.addProperty("y", 2.0D);
		object.addProperty("z", 3.0D);

		Spot spot = Spot.fromJson(object, null);
		assertEquals(0.0F, spot.yaw());
		assertEquals(0.0F, spot.pitch());
	}
}
