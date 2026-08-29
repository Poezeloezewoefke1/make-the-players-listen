package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The times a course keeps, and which of them it throws away. */
class LeaderboardTest {
	@TempDir
	Path directory;

	private LobbyState state;

	@BeforeEach
	void setUp() {
		state = LobbyState.get();
		state.load(directory.resolve("lobby.json"));
	}

	private static UUID player(int index) {
		return new UUID(0L, index);
	}

	@Test
	void onlyTheBestRunOfEachPlayerIsKept() {
		assertTrue(state.recordTime("tower", new CourseRecord(player(1), "One", 40_000L, 1L)));
		assertFalse(state.recordTime("tower", new CourseRecord(player(1), "One", 50_000L, 2L)));
		assertTrue(state.recordTime("tower", new CourseRecord(player(1), "One", 30_000L, 3L)));

		assertEquals(1, state.leaderboard("tower").size());
		assertEquals(30_000L, state.personalBest("tower", player(1)).millis());
	}

	@Test
	void anEqualTimeIsNotAnImprovement() {
		assertTrue(state.recordTime("tower", new CourseRecord(player(1), "One", 40_000L, 1L)));
		assertFalse(state.recordTime("tower", new CourseRecord(player(1), "One", 40_000L, 2L)),
				"matching your best is not beating it");
	}

	@Test
	void theBoardStaysSortedHoweverTheTimesArriveq() {
		state.recordTime("tower", new CourseRecord(player(1), "One", 50_000L, 1L));
		state.recordTime("tower", new CourseRecord(player(2), "Two", 10_000L, 2L));
		state.recordTime("tower", new CourseRecord(player(3), "Three", 30_000L, 3L));

		List<CourseRecord> board = state.leaderboard("tower");
		assertEquals(List.of(10_000L, 30_000L, 50_000L),
				board.stream().map(CourseRecord::millis).toList());
	}

	@Test
	void aBusyBoardStillRemembersEverybodysBest() {
		// The board is also where "your best is still" is read from, so somebody falling off the
		// end of it would start being told every slow run was a personal best.
		for (int index = 1; index <= 150; index++) {
			state.recordTime("tower", new CourseRecord(player(index), "P" + index, index * 1000L, index));
		}

		assertEquals(150, state.leaderboard("tower").size());

		CourseRecord slowest = state.personalBest("tower", player(150));
		assertNotNull(slowest, "the slowest runner should still have a recorded best");
		assertEquals(150_000L, slowest.millis());

		assertFalse(state.recordTime("tower", new CourseRecord(player(150), "P150", 160_000L, 999L)),
				"and a slower run than it is still not a personal best");
	}

	@Test
	void aCourseWithNoTimesAnswersEmptyRatherThanNull() {
		assertNotNull(state.leaderboard("nothing-here"));
		assertTrue(state.leaderboard("nothing-here").isEmpty());
		assertNull(state.personalBest("nothing-here", player(1)));
	}

	@Test
	void timesAreKeptPerCourseAndNotSharedBetweenThem() {
		state.recordTime("tower", new CourseRecord(player(1), "One", 10_000L, 1L));
		state.recordTime("bridge", new CourseRecord(player(1), "One", 20_000L, 2L));

		assertEquals(10_000L, state.personalBest("tower", player(1)).millis());
		assertEquals(20_000L, state.personalBest("bridge", player(1)).millis());
	}

	@Test
	void theCourseNameIsMatchedWithoutRegardToCase() {
		state.recordTime("Tower", new CourseRecord(player(1), "One", 10_000L, 1L));

		assertEquals(1, state.leaderboard("tower").size());
		assertEquals(1, state.leaderboard("TOWER").size());
	}

	@Test
	void theTimesSurviveARestart() {
		state.recordTime("tower", new CourseRecord(player(1), "One", 10_000L, 1L));
		state.recordTime("tower", new CourseRecord(player(2), "Two", 20_000L, 2L));
		state.flush();
		state.load(directory.resolve("lobby.json"));

		assertEquals(2, state.leaderboard("tower").size());
		assertEquals(10_000L, state.leaderboard("tower").get(0).millis());
	}
}
