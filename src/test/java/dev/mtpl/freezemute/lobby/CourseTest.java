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
