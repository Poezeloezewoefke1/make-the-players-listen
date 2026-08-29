package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import dev.mtpl.freezemute.lobby.LobbyState.Admitted;
import dev.mtpl.freezemute.lobby.LobbyState.Waiting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LobbyStateTest {
	private static final UUID STEVE = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID ALEX = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID ZOE = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@TempDir
	Path directory;

	private Path file;
	private LobbyState state;

	@BeforeEach
	void setUp() {
		file = directory.resolve("lobby.json");
		state = LobbyState.get();
		state.load(file);
	}

	@Test
	void startsEmptyAndOpen() {
		assertFalse(state.enabled());
		assertTrue(state.queueOpen());
		assertEquals(0, state.cap());
		assertEquals(0, state.queueSize());
		assertEquals(0, state.slotsUsed());
	}

	@Test
	void theLineIsFirstComeFirstServed() {
		state.enqueue(STEVE, "Steve", 1000L);
		state.enqueue(ALEX, "Alex", 2000L);
		state.enqueue(ZOE, "Zoe", 3000L);

		assertEquals(1, state.position(STEVE));
		assertEquals(2, state.position(ALEX));
		assertEquals(3, state.position(ZOE));
		assertEquals(3, state.queueSize());
	}

	@Test
	void queueingTwiceDoesNotMoveYou() {
		state.enqueue(STEVE, "Steve", 1000L);
		state.enqueue(ALEX, "Alex", 2000L);
		state.enqueue(STEVE, "Steve", 5000L);

		assertEquals(1, state.position(STEVE));
		assertEquals(2, state.queueSize());
	}

	@Test
	void leavingKeepsThePlaceAndComingBackKeepsItToo() {
		state.enqueue(STEVE, "Steve", 1000L);
		state.enqueue(ALEX, "Alex", 2000L);
		state.markWaitingOffline(STEVE, 4000L);

		Waiting waiting = state.waiting(STEVE);
		assertNotNull(waiting);
		assertFalse(waiting.online());
		assertEquals(4000L, waiting.offlineSince());
		assertEquals(1, state.position(STEVE), "an offline player keeps their place");

		state.enqueue(STEVE, "Steve", 5000L);
		assertTrue(state.waiting(STEVE).online());
		assertEquals(1, state.position(STEVE));
	}

	@Test
	void nextOnlineSkipsWhoeverIsNotThere() {
		state.enqueue(STEVE, "Steve", 1000L);
		state.enqueue(ALEX, "Alex", 2000L);
		state.markWaitingOffline(STEVE, 3000L);

		Waiting next = state.nextOnline();
		assertNotNull(next);
		assertEquals(ALEX, next.uuid());
	}

	@Test
	void anOfflineSlotIsStillATakenSlot() {
		state.admit(STEVE, "Steve", 1000L);
		state.markAdmittedOffline(STEVE, 2000L);

		assertEquals(1, state.slotsUsed());
		Admitted entry = state.admittedEntry(STEVE);
		assertNotNull(entry);
		assertFalse(entry.online());

		assertNotNull(state.release(STEVE));
		assertEquals(0, state.slotsUsed());
	}

	@Test
	void comingBackDoesNotResetHowLongYouHaveBeenIn() {
		state.admit(STEVE, "Steve", 1000L);
		state.markAdmittedOffline(STEVE, 2000L);
		state.admit(STEVE, "Steve", 9000L);

		Admitted entry = state.admittedEntry(STEVE);
		assertEquals(1000L, entry.since());
		assertTrue(entry.online());
	}

	@Test
	void onlyTheBestTimePerPlayerIsKept() {
		assertTrue(state.recordTime("tower", new CourseRecord(STEVE, "Steve", 30_000L, 1L)));
		assertFalse(state.recordTime("tower", new CourseRecord(STEVE, "Steve", 45_000L, 2L)),
				"a slower run is not a personal best");
		assertTrue(state.recordTime("tower", new CourseRecord(STEVE, "Steve", 20_000L, 3L)));

		List<CourseRecord> board = state.leaderboard("tower");
		assertEquals(1, board.size());
		assertEquals(20_000L, board.get(0).millis());
	}

	@Test
	void theBoardIsSortedFastestFirst() {
		state.recordTime("tower", new CourseRecord(STEVE, "Steve", 30_000L, 1L));
		state.recordTime("tower", new CourseRecord(ALEX, "Alex", 12_000L, 2L));
		state.recordTime("tower", new CourseRecord(ZOE, "Zoe", 20_000L, 3L));

		List<CourseRecord> board = state.leaderboard("tower");
		assertEquals(List.of("Alex", "Zoe", "Steve"), board.stream().map(CourseRecord::name).toList());
		assertEquals(12_000L, state.personalBest("tower", ALEX).millis());
	}

	@Test
	void courseNamesAreCaseInsensitive() {
		state.putCourse(Course.starting("Tower", new Spot(1.0D, 2.0D, 3.0D, 0.0F, 0.0F)));

		assertNotNull(state.course("tower"));
		assertNotNull(state.course("TOWER"));
		assertEquals("Tower", state.course("tower").name(), "the name keeps the capitals it was given");
	}

	@Test
	void deletingACourseTakesItsTimesWithIt() {
		state.putCourse(Course.starting("tower", new Spot(1.0D, 2.0D, 3.0D, 0.0F, 0.0F)));
		state.recordTime("tower", new CourseRecord(STEVE, "Steve", 30_000L, 1L));

		assertTrue(state.removeCourse("tower"));
		assertTrue(state.leaderboard("tower").isEmpty());
		assertFalse(state.removeCourse("tower"));
	}

	@Test
	void everythingSurvivesARestart() {
		state.setEnabled(true);
		state.setCap(8);
		state.setQueueOpen(false);
		state.setSpawn(new Spot(10.5D, 65.0D, -4.5D, 90.0F, 5.0F));
		state.enqueue(STEVE, "Steve", 1000L);
		state.enqueue(ALEX, "Alex", 2000L);
		state.markWaitingOffline(ALEX, 2500L);
		state.admit(ZOE, "Zoe", 3000L);
		state.addEarlyAccess(STEVE, "Steve");
		state.rememberReturn(STEVE, "minecraft:overworld", new Spot(1.0D, 2.0D, 3.0D, 4.0F, 5.0F), "creative");
		state.putCourse(Course.starting("tower", new Spot(0.0D, 64.0D, 0.0D, 0.0F, 0.0F))
				.withCheckpoint(new Spot(5.0D, 70.0D, 0.0D, 0.0F, 0.0F))
				.withFinish(new Spot(10.0D, 80.0D, 0.0D, 0.0F, 0.0F)));
		state.recordTime("tower", new CourseRecord(ZOE, "Zoe", 42_000L, 4000L));

		restart();

		assertTrue(state.enabled());
		assertEquals(8, state.cap());
		assertFalse(state.queueOpen());
		assertEquals(10.5D, state.spawn().x());
		assertEquals(90.0F, state.spawn().yaw());
		assertEquals(2, state.queueSize());
		assertEquals(1, state.position(STEVE));
		assertFalse(state.waiting(ALEX).online());
		assertEquals(2500L, state.waiting(ALEX).offlineSince());
		assertTrue(state.isAdmitted(ZOE));
		assertTrue(state.hasEarlyAccess(STEVE));
		assertEquals(STEVE, state.earlyAccessByName("steve"));

		LobbyState.Return spot = state.takeReturn(STEVE);
		assertNotNull(spot);
		assertEquals("minecraft:overworld", spot.dimension());
		assertEquals(3.0D, spot.spot().z());
		assertEquals("creative", spot.gameMode(), "a builder is not demoted to survival on the way out");

		Course course = state.course("tower");
		assertNotNull(course);
		assertEquals(1, course.checkpoints().size());
		assertTrue(course.playable());
		assertEquals(42_000L, state.leaderboard("tower").get(0).millis());
	}

	/** What a restart does: the pending write is closed, then the file is read back. */
	private void restart() {
		state.flush();
		state.load(file);
	}

	@Test
	void changesAreWrittenOnceRatherThanOnEveryChange() throws Exception {
		state.setCap(4);
		state.enqueue(STEVE, "Steve", 1000L);

		assertFalse(java.nio.file.Files.exists(file), "nothing should have been written yet");

		state.flush();
		assertTrue(java.nio.file.Files.exists(file), "the flush is what writes it");

		long written = java.nio.file.Files.size(file);
		state.flush();
		assertEquals(written, java.nio.file.Files.size(file), "a flush with nothing pending writes nothing new");
	}

	@Test
	void aFlushWithNothingToSayDoesNotCreateAFile() {
		state.flush();
		assertFalse(java.nio.file.Files.exists(file));
	}

	@Test
	void loadingClearsAWriteThatWasStillPending() throws Exception {
		// Reading a file has to cancel whatever was queued to be written, or the load is undone
		// by a flush of the state it just replaced.
		state.setCap(9);
		state.load(directory.resolve("somewhere-else.json"));
		state.flush();

		assertFalse(java.nio.file.Files.exists(directory.resolve("somewhere-else.json")));
		assertEquals(0, state.cap());
	}

	@Test
	void aPlayerCanBeFoundByNameWhereverTheyAre() {
		// /queue remove works on a name, so it has to find people in the line, people holding a
		// slot, and people who have gone offline and are sitting on their grace window.
		state.enqueue(STEVE, "Steve", 1000L);
		state.admit(ALEX, "Alex", 2000L);
		state.markAdmittedOffline(ALEX, 2500L);

        assertEquals(STEVE, state.knownByName("steve"));
		assertEquals(ALEX, state.knownByName("ALEX"));
		assertNull(state.knownByName("Nobody"));
	}

	@Test
	void takingSomebodyOutOfTheLineClosesBothWaysTheyCouldBeIn() {
		state.enqueue(STEVE, "Steve", 1000L);
		state.admit(STEVE, "Steve", 1000L);

		assertNotNull(state.dequeue(STEVE));
		assertNotNull(state.release(STEVE));
		assertEquals(0, state.queueSize());
		assertEquals(0, state.slotsUsed());
		assertNull(state.dequeue(STEVE));
	}

	@Test
	void anUnreadableFileDoesNotTakeTheLobbyDown() throws Exception {
		java.nio.file.Files.writeString(file, "this is not json at all");
		state.load(file);

		assertFalse(state.enabled());
		assertEquals(0, state.queueSize());
		assertNull(state.course("tower"));
	}
}
