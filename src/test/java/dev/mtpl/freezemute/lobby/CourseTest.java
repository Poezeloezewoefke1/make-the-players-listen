package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CourseTest {
	private static Spot at(double x, double y, double z) {
		return new Spot(x, y, z, 0.0F, 0.0F);
	}

	// ------------------------------------------ pads the start would swallow

	@Test
	void aFinishOnTopOfTheStartCouldNeverBeReached() {
		Spot start = at(10.0D, 65.0D, 10.0D);

		assertTrue(Parkour.swallowedByTheStart(start, at(10.0D, 65.0D, 10.0D), 1.5D));
		assertTrue(Parkour.swallowedByTheStart(start, at(12.0D, 65.0D, 10.0D), 1.5D),
				"three blocks is inside the start pad at the default radius");
	}

	@Test
	void aPadFarEnoughAwayIsFine() {
		Spot start = at(10.0D, 65.0D, 10.0D);

		assertFalse(Parkour.swallowedByTheStart(start, at(20.0D, 65.0D, 10.0D), 1.5D));
		assertFalse(Parkour.swallowedByTheStart(start, at(10.0D, 75.0D, 10.0D), 1.5D),
				"straight up is still away");
	}

	@Test
	void theRuleIsTheSameWhicheverEndItIsAskedFrom() {
		// Moving the finish onto the start and moving the start onto the finish make the same
		// unfinishable course, so they have to be refused by the same measurement.
		Spot a = at(10.0D, 65.0D, 10.0D);
		Spot b = at(11.0D, 65.0D, 10.0D);

		assertEquals(Parkour.swallowedByTheStart(a, b, 1.5D), Parkour.swallowedByTheStart(b, a, 1.5D));
	}

	@Test
	void aCourseWithNoFinishYetIsNotAnError() {
		assertFalse(Parkour.swallowedByTheStart(at(1.0D, 2.0D, 3.0D), null, 1.5D),
				"there is nothing there to be swallowed");
		assertFalse(Parkour.swallowedByTheStart(null, at(1.0D, 2.0D, 3.0D), 1.5D));
	}

	@Test
	void aSillyCheckpointRadiusStillLeavesAUsableStartPad() {
		assertTrue(Parkour.startRadius(0.0D) >= 1.0D, "a radius of zero would refuse nothing");
		assertTrue(Parkour.startRadius(-5.0D) >= 1.0D);
		assertEquals(3.0D, Parkour.startRadius(1.5D));
	}

	@Test
	void aFreshCourseHasNoFinishSoItCannotBeRunYet() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D));

		assertFalse(course.playable());
		assertTrue(course.checkpoints().isEmpty());
	}

	@Test
	void checkpointsAreKeptInTheOrderTheyWereAdded() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D))
				.withCheckpoint(at(1.0D, 64.0D, 0.0D))
				.withCheckpoint(at(2.0D, 64.0D, 0.0D))
				.withFinish(at(3.0D, 64.0D, 0.0D));

		assertEquals(2, course.checkpoints().size());
		assertEquals(1.0D, course.checkpoints().get(0).x());
		assertEquals(2.0D, course.checkpoints().get(1).x());
		assertTrue(course.playable());
	}

	@Test
	void undoTakesTheLastOneBack() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D))
				.withCheckpoint(at(1.0D, 64.0D, 0.0D))
				.withCheckpoint(at(2.0D, 64.0D, 0.0D))
				.withoutLastCheckpoint();

		assertEquals(1, course.checkpoints().size());
		assertEquals(1.0D, course.checkpoints().get(0).x());
	}

	@Test
	void undoOnACourseWithNoCheckpointsChangesNothing() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D));
		assertSame(course, course.withoutLastCheckpoint());
	}

	@Test
	void aFallBeforeTheFirstCheckpointGoesBackToTheStart() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D))
				.withCheckpoint(at(1.0D, 64.0D, 0.0D));

		assertEquals(0.0D, course.respawnFor(0).x());
	}

	@Test
	void aFallGoesBackToTheLastCheckpointTaken() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D))
				.withCheckpoint(at(1.0D, 64.0D, 0.0D))
				.withCheckpoint(at(2.0D, 64.0D, 0.0D));

		assertEquals(1.0D, course.respawnFor(1).x());
		assertEquals(2.0D, course.respawnFor(2).x());
	}

	@Test
	void moreCheckpointsThanExistStillLandsOnTheLastOne() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D))
				.withCheckpoint(at(1.0D, 64.0D, 0.0D));

		assertEquals(1.0D, course.respawnFor(9).x());
	}

	@Test
	void aCourseSurvivesBeingWrittenOutAndReadBack() {
		Course course = Course.starting("tower", new Spot(0.5D, 64.0D, 0.5D, 90.0F, -10.0F))
				.withCheckpoint(at(1.0D, 70.0D, 0.0D))
				.withFinish(at(2.0D, 80.0D, 0.0D));

		Course again = Course.fromJson(course.toJson());

		assertNotNull(again);
		assertEquals("tower", again.name());
		assertEquals(90.0F, again.start().yaw());
		assertEquals(1, again.checkpoints().size());
		assertEquals(80.0D, again.finish().y());
	}

	@Test
	void anUnfinishedCourseAlsoSurvivesTheTrip() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D));
		Course again = Course.fromJson(course.toJson());

		assertNotNull(again);
		assertNull(again.finish());
		assertFalse(again.playable());
	}

	@Test
	void rubbishJsonIsRejectedRatherThanGuessedAt() {
		assertNull(Course.fromJson(new com.google.gson.JsonObject()));
	}

	@Test
	void checkpointsCannotBeChangedThroughTheListYouGetBack() {
		Course course = Course.starting("tower", at(0.0D, 64.0D, 0.0D))
				.withCheckpoint(at(1.0D, 64.0D, 0.0D));

		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
				() -> course.checkpoints().add(at(2.0D, 64.0D, 0.0D)));
	}
}
