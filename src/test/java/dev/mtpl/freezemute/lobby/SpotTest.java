package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class SpotTest {
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
