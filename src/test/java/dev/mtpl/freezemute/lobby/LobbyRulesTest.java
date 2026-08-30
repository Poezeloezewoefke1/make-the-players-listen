package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import dev.mtpl.freezemute.FreezeMuteConfig;
import dev.mtpl.freezemute.lobby.FakeRoom.FakePlayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The lobby's once-a-second pass, driven end to end.
 *
 * <p>Every bug found in the lobby so far has been in here somewhere - people teleported in and
 * straight back out, boss bars left showing a place nobody holds, players who walked out of the
 * room and stayed out, staff quietly holding a slot. None of it could be tested before, because
 * it took a running server and people standing in it. It does not any more.
 */
class LobbyRulesTest {
	private static final long GRACE = 300_000L;

	@TempDir
	Path directory;

	private LobbyState state;
	private FreezeMuteConfig config;
	private FakeRoom room;

	@BeforeEach
	void setUp() {
		state = LobbyState.get();
		state.load(directory.resolve("lobby.json"));
		state.setEnabled(true);
		config = FreezeMuteConfig.get();
		room = new FakeRoom();
	}

	/** One second of the lobby thinking. */
	private void tick(long now) {
		LobbyRules.tickSecond(room, state, config, now);
	}

	// ------------------------------------------------------- letting people in

	@Test
	void withRoomToSpareTheFrontOfTheLineGoesStraightThrough() {
		state.setCap(2);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		FakePlayer ben = room.add("Ben").standingInTheLobby();

		// One a second by default, so two seconds for two people.
		tick(1000L);
		tick(2000L);

		assertEquals(1, anna.admitted);
		assertEquals(1, ben.admitted);
		assertTrue(state.isAdmitted(anna.uuid()));
	}

	@Test
	void aFullCapLeavesEverybodyElseWaitingWithABar() {
		state.setCap(1);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		FakePlayer ben = room.add("Ben").standingInTheLobby();

		tick(1000L);

		assertEquals(1, anna.admitted, "the first one in line goes through");
		assertEquals(0, ben.admitted, "the second waits");
		assertEquals("place 1 of 1", ben.bar, "and is told where he stands");
	}

	@Test
	void theThrottleLimitsHowManyGoThroughEachSecond() {
		int wasAllowed = config.lobbyAdmitPerSecond;

		try {
			config.lobbyAdmitPerSecond = 1;
			state.setCap(0);
			FakePlayer anna = room.add("Anna").standingInTheLobby();
			FakePlayer ben = room.add("Ben").standingInTheLobby();
			FakePlayer cara = room.add("Cara").standingInTheLobby();

			// The first tick queues all three and lets one through.
			tick(1000L);
			assertEquals(1, anna.admitted + ben.admitted + cara.admitted);

			tick(2000L);
			assertEquals(2, anna.admitted + ben.admitted + cara.admitted);
		} finally {
			config.lobbyAdmitPerSecond = wasAllowed;
		}
	}

	@Test
	void aClosedQueueLetsNobodyThroughButStillKeepsThemInOrder() {
		state.setCap(0);
		state.setQueueOpen(false);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		FakePlayer ben = room.add("Ben").standingInTheLobby();

		tick(1000L);

		assertEquals(0, anna.admitted);
		assertEquals(0, ben.admitted);
		assertEquals("closed 1 of 2", anna.bar);
		assertEquals("closed 2 of 2", ben.bar);
	}

	// ------------------------------------------------------------------- staff

	@Test
	void staffAreNeverHeldNeverQueuedAndNeverBarred() {
		state.setCap(1);
		FakePlayer mod = room.add("Mod").staff(true).standingInTheLobby();
		FakePlayer anna = room.add("Anna").standingInTheLobby();

		tick(1000L);

		assertFalse(mod.member, "staff are not held by the room they are standing in");
		assertNull(mod.bar);
		assertEquals(0, state.position(mod.uuid()), "and are not in the line");
		assertEquals(1, anna.admitted, "so the slot goes to the player who was waiting");
	}

	@Test
	void beingMadeAnOperatorInTheRoomLetsGoOfSomebody() {
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);
		anna.member = true;

		anna.staff(true);
		tick(2000L);

		assertFalse(anna.member, "an operator is not held by the lobby");
	}

	@Test
	void stoppingBeingAnOperatorInTheRoomPicksSomebodyUp() {
		// Shut, or they would be let straight through and stop being held for that reason instead.
		state.setQueueOpen(false);
		FakePlayer mod = room.add("Mod").staff(true).standingInTheLobby();
		tick(1000L);
		assertFalse(mod.member);

		mod.staff(false);
		tick(2000L);

		assertTrue(mod.member, "an ordinary player standing in the room is held by it");
	}

	// ------------------------------------------------------------- wandering off

	@Test
	void somebodyWhoLeavesTheRoomWithoutBeingLetInIsWalkedBack() {
		state.setCap(0);
		state.setQueueOpen(false);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);
		assertTrue(anna.member);

		// What /kill, a death, or another mod's /home does.
		anna.wanderOff();
		tick(2000L);

		assertTrue(anna.inLobby, "they should be back in the room");
		assertEquals(1, anna.sentToLobby);
		assertTrue(room.log.stream().anyMatch(line -> line.contains("left the lobby without being let in")));
	}

	@Test
	void nobodyIsWalkedBackToARoomThatIsNotThere() {
		state.setQueueOpen(false);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);
		anna.wanderOff();

		room.built = false;
		tick(2000L);
		tick(3000L);

		assertEquals(0, anna.sentToLobby, "apologising once a second is worse than the hole it plugs");
	}

	@Test
	void beingLetThroughIsNotMistakenForWanderingOff() {
		state.setCap(0);
		FakePlayer anna = room.add("Anna").standingInTheLobby();

		tick(1000L);
		tick(2000L);
		tick(3000L);

		assertEquals(1, anna.admitted);
		assertEquals(0, anna.sentToLobby, "somebody who was let in is not dragged back");
	}

	// ------------------------------------------------------------ the boss bar

	@Test
	void theBarGoesAwayWhenTheLineIsCleared() {
		state.setQueuePoint(new Spot(0.5D, 65.0D, 0.5D, 0.0F, 0.0F));
		state.setCap(1);
		FakePlayer ben = room.add("Ben").standingInTheLobby();
		state.enqueue(ben.uuid(), "Ben", 1000L);
		state.admit(new UUID(0L, 99L), "Anna", 1000L);
		tick(1000L);
		assertEquals("place 1 of 1", ben.bar);

		// What /queue end does.
		state.setQueueOpen(false);
		state.clearQueue();
		tick(2000L);

		assertNull(ben.bar, "a bar showing a place nobody holds is worse than no bar");
	}

	@Test
	void withNoPedestalClearingTheLineOnlyMovesPeopleToTheBackOfIt() {
		state.setCap(1);
		room.add("Anna").standingInTheLobby();
		FakePlayer ben = room.add("Ben").standingInTheLobby();
		tick(1000L);
		assertEquals("place 1 of 1", ben.bar);

		// What /queue end does. With nowhere to ask, standing in the room is the asking, so the
		// sweep puts Ben straight back in the line he was just taken out of - and his bar comes
		// back saying so rather than vanishing. That is the honest answer: he really is first in
		// a line that really is closed. /queue end says this will happen; /queue remove too.
		state.setQueueOpen(false);
		state.clearQueue();
		tick(2000L);

		assertEquals("closed 1 of 1", ben.bar, "the bar should say where he stands, not disappear");
		assertEquals(1, state.position(ben.uuid()));
	}

	@Test
	void theBarFollowsSomebodyUpTheLine() {
		state.setCap(1);
		room.add("Anna").standingInTheLobby();
		FakePlayer ben = room.add("Ben").standingInTheLobby();
		FakePlayer cara = room.add("Cara").standingInTheLobby();
		tick(1000L);

		assertEquals("place 1 of 2", ben.bar);
		assertEquals("place 2 of 2", cara.bar);

		state.setCap(2);
		tick(2000L);

		assertEquals(1, ben.admitted);
		assertEquals("place 1 of 1", cara.bar, "and the one behind moves up");
	}

	// ------------------------------------------------------- the grace windows

	@Test
	void aSlotIsHeldForSomebodyWhoDroppedOutAndThenGivenAway() {
		state.setCap(1);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		FakePlayer ben = room.add("Ben").standingInTheLobby();
		tick(1000L);
		assertEquals(1, anna.admitted);

		room.disconnect(anna);
		state.markAdmittedOffline(anna.uuid(), 2000L);

		tick(2000L + GRACE - 1000L);
		assertEquals(0, ben.admitted, "Ben cannot have a slot Anna is still holding");

		tick(2000L + GRACE);
		assertEquals(1, ben.admitted, "and now it is his");
	}

	@Test
	void aPlaceInLineIsHeldForSomebodyWhoDroppedOutAndThenGivenUp() {
		state.setCap(0);
		state.setQueueOpen(false);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);
		assertEquals(1, state.position(anna.uuid()));

		room.disconnect(anna);
		state.markWaitingOffline(anna.uuid(), 2000L);

		tick(2000L + GRACE - 1000L);
		assertEquals(1, state.position(anna.uuid()));

		tick(2000L + GRACE);
		assertEquals(0, state.position(anna.uuid()));
	}

	@Test
	void anEntrySayingOnlineWithNobodyThereIsCorrectedRatherThanLoopedOn() {
		state.setCap(0);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		FakePlayer ben = room.add("Ben").standingInTheLobby();
		tick(1000L);

		// Anna is queued and marked online, but her connection is gone without a leave.
		state.enqueue(anna.uuid(), "Anna", 2000L);
		room.disconnect(anna);
		state.enqueue(ben.uuid(), "Ben", 2100L);

		tick(3000L);

		assertFalse(state.waiting(anna.uuid()) == null && state.isAdmitted(anna.uuid()),
				"she is either still in the line or was admitted, not silently lost");
		assertEquals(1, ben.admitted, "and the person who is actually here gets through");
	}

	// ------------------------------------------------ coming back from a crash

	/** Writes a lobby file by hand, the way a crash leaves one: everybody still marked online. */
	private Path crashedWith(String json) throws Exception {
		Path path = directory.resolve("crashed.json");
		java.nio.file.Files.writeString(path, json);
		return path;
	}

	@Test
	void aCrashDoesNotLeaveSlotsHeldForEver() throws Exception {
		// Two people held slots and one was waiting when the server went down. Nothing recorded a
		// disconnect, so the file says all three are online.
		state.load(crashedWith("""
				{
				  "enabled": true, "queueOpen": true, "cap": 2,
				  "queue": [ { "uuid": "00000000-0000-0000-0000-000000000009", "name": "Zoe", "joinedAt": 1 } ],
				  "admitted": [
				    { "uuid": "00000000-0000-0000-0000-000000000007", "name": "Gone", "since": 1 },
				    { "uuid": "00000000-0000-0000-0000-000000000008", "name": "Also", "since": 2 }
				  ]
				}
				"""));

		assertEquals(2, state.slotsUsed(), "the file said two slots were taken");

		// Nobody comes back. A long time passes.
		tick(1000L);
		tick(System.currentTimeMillis() + GRACE + 60_000L);

		assertEquals(0, state.slotsUsed(), "a slot nobody is holding is a slot nobody can use");
		assertEquals(0, state.queueSize(), "and the same goes for a place in line");
	}

	@Test
	void aCrashedLobbyLetsTheNextPersonInOnceTheGhostsAreGone() throws Exception {
		state.load(crashedWith("""
				{
				  "enabled": true, "queueOpen": true, "cap": 1,
				  "admitted": [ { "uuid": "00000000-0000-0000-0000-000000000007", "name": "Gone", "since": 1 } ]
				}
				"""));

		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);

		assertEquals(0, anna.admitted, "the only slot is still held while the window runs");

		tick(System.currentTimeMillis() + GRACE + 60_000L);

		assertEquals(1, anna.admitted, "and handed on once it does not");
	}

	@Test
	void somebodyStandingInTheRoomIsNeverSweptOutOfIt() throws Exception {
		// With a queue point set, so that being swept out of the line stays swept - without one
		// the stray collector would put her back a second later and hide the bug.
		state.load(crashedWith("""
				{
				  "enabled": true, "queueOpen": false, "cap": 1,
				  "queuePoint": { "x": 0.5, "y": 65.0, "z": 0.5 },
				  "queue": [ { "uuid": "00000000-0000-0000-0000-000000000001", "name": "Anna", "joinedAt": 77 } ]
				}
				"""));

		// The same player reconnects. Their entry still says they left.
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		assertEquals(new UUID(0L, 1L), anna.uuid(), "the fake hands out ids in order");

		tick(1000L);
		tick(System.currentTimeMillis() + GRACE + 60_000L);

		assertEquals(1, state.position(anna.uuid()),
				"she is standing right there; a window counted down against her is a bug");
		assertEquals(77L, state.waiting(anna.uuid()).joinedAt(),
				"and it is the place she already had, not a new one at the back");
	}

	@Test
	void aRenameFollowsSomebodyThroughTheLine() throws Exception {
		state.load(crashedWith("""
				{
				  "enabled": true, "queueOpen": false, "cap": 1,
				  "queue": [ { "uuid": "00000000-0000-0000-0000-000000000001", "name": "OldName", "joinedAt": 1 } ]
				}
				"""));

		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);

		assertEquals("Anna", state.waiting(anna.uuid()).name(),
				"/queue remove works on a name, so the stored one has to be the current one");
	}

	// ------------------------------------------------------- gaining operator

	@Test
	void beingMadeAnOperatorWhileWaitingLetsYouOut() {
		state.setCap(0);
		state.setQueueOpen(false);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);

		assertTrue(anna.member, "she is held by the room to begin with");
		assertEquals(1, state.position(anna.uuid()));

		// What /op does.
		anna.staff(true);
		tick(2000L);

		assertEquals(1, anna.letOut, "left standing there she would keep adventure mode, keep a "
				+ "place she can never be let through, and stay invisible to the room");
		assertFalse(anna.member);
		assertFalse(anna.inLobby);
		assertEquals(0, state.position(anna.uuid()), "and she is out of the line, not still in it");
	}

	@Test
	void staffWhoCameToLookAroundAreLeftWhereTheyAre() {
		FakePlayer mod = room.add("Mod").standingInTheLobby();
		mod.staff(true);

		tick(1000L);
		tick(2000L);
		tick(3000L);

		assertEquals(0, mod.letOut, "/lobby is how staff go and look at the room, not a round trip");
		assertTrue(mod.inLobby);
		assertEquals(0, state.position(mod.uuid()), "and looking at it is not asking for a place");
	}

	@Test
	void anOperatorWhoLosesItStartsWaitingLikeAnybodyElse() {
		state.setCap(0);
		state.setQueueOpen(false);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		anna.staff(true);
		tick(1000L);
		assertFalse(anna.member);

		// What /deop does.
		anna.staff(false);
		tick(2000L);

		assertTrue(anna.member, "the room holds her now");
		assertEquals(1, state.position(anna.uuid()));
		assertEquals(0, anna.letOut);
	}

	@Test
	void anOperatorDoesNotKeepASlotTheyCannotUse() {
		state.setCap(1);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);
		assertTrue(state.isAdmitted(anna.uuid()), "she was let through and holds the only slot");

		// She is back in the room - sent there by staff, say - and then made an operator.
		anna.inLobby = true;
		anna.member = true;
		anna.staff(true);
		tick(2000L);

		assertFalse(state.isAdmitted(anna.uuid()), "staff do not hold slots; that one was blocking "
				+ "somebody who could have used it");
		assertEquals(0, state.slotsUsed());
	}

	// -------------------------------------------------------- the queue point

	@Test
	void withAPedestalStandingInTheRoomDoesNotPutYouInTheLine() {
		state.setQueuePoint(new Spot(0.5D, 65.0D, 0.5D, 0.0F, 0.0F));
		state.setCap(0);
		FakePlayer anna = room.add("Anna").standingInTheLobby();

		tick(1000L);
		tick(2000L);

		assertEquals(0, state.position(anna.uuid()), "she has not asked yet");
		assertEquals(0, anna.admitted, "so she is not let through");
		assertTrue(anna.member, "but the room still holds her");
	}

	@Test
	void withAPedestalAskingIsWhatPutsYouInTheLine() {
		state.setQueuePoint(new Spot(0.5D, 65.0D, 0.5D, 0.0F, 0.0F));
		state.setCap(0);
		FakePlayer anna = room.add("Anna").standingInTheLobby();
		tick(1000L);

		// What clicking the pedestal does.
		state.enqueue(anna.uuid(), "Anna", 2000L);
		tick(2000L);

		assertEquals(1, anna.admitted);
	}

	@Test
	void withNoPedestalStandingInTheRoomIsAsking() {
		state.setCap(0);
		state.setQueueOpen(false);
		FakePlayer anna = room.add("Anna").standingInTheLobby();

		tick(1000L);

		assertEquals(1, state.position(anna.uuid()));
	}

	// ------------------------------------------------------------------ order

	@Test
	void theLineIsServedInTheOrderPeopleJoinedIt() {
		state.setCap(0);
		int wasAllowed = config.lobbyAdmitPerSecond;

		try {
			config.lobbyAdmitPerSecond = 1;
			FakePlayer anna = room.add("Anna").standingInTheLobby();
			FakePlayer ben = room.add("Ben").standingInTheLobby();
			FakePlayer cara = room.add("Cara").standingInTheLobby();

			tick(1000L);
			tick(2000L);
			tick(3000L);

			assertEquals(1, anna.admitted);
			assertEquals(1, ben.admitted);
			assertEquals(1, cara.admitted);
		} finally {
			config.lobbyAdmitPerSecond = wasAllowed;
		}
	}

	@Test
	void aQuietRoomDoesNothingAtAll() {
		room.add("Mod").staff(true);
		room.add("Anna");

		tick(1000L);
		tick(2000L);

		assertEquals(0, state.queueSize());
		assertEquals(0, state.slotsUsed());
		assertTrue(room.log.isEmpty());
	}
}
