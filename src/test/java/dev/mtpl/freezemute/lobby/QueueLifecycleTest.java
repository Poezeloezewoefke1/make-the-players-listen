package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import dev.mtpl.freezemute.lobby.LobbyState.Waiting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A whole evening in the lobby, run through the state the lobby actually keeps.
 *
 * <p>The individual pieces are covered elsewhere. What these do is put them in the order a real
 * session puts them in, because the mistakes that matter are the ones between the pieces - a
 * place given away while somebody was reconnecting, a slot still held by somebody who left.
 */
class QueueLifecycleTest {
	private static final UUID ANNA = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID BEN = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID CARA = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final long GRACE = 300_000L;

	@TempDir
	Path directory;

	private LobbyState state;

	@BeforeEach
	void setUp() {
		state = LobbyState.get();
		state.load(directory.resolve("lobby.json"));
	}

	/** What the ticker does once a second, without needing a server to do it to. */
	private void sweep(long now) {
		for (Waiting entry : state.queue()) {
			if (entry.graceRanOut(now, GRACE)) {
				state.dequeue(entry.uuid());
			}
		}

		for (LobbyState.Admitted entry : state.admitted()) {
			if (entry.graceRanOut(now, GRACE)) {
				state.release(entry.uuid());
			}
		}
	}

	/** Lets people through while the cap allows it, oldest first. */
	private int admitWhileThereIsRoom(long now) {
		int admitted = 0;

		while (LobbyManager.hasFreeSlot(state)) {
			Waiting next = state.nextOnline();

			if (next == null) {
				break;
			}

			state.dequeue(next.uuid());
			state.admit(next.uuid(), next.name(), now);
			admitted++;
		}

		return admitted;
	}

	@Test
	void threePeopleQueueAndACapOfTwoLetsTwoThrough() {
		state.setCap(2);
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);
		state.enqueue(CARA, "Cara", 3000L);

		assertEquals(2, admitWhileThereIsRoom(4000L));
		assertTrue(state.isAdmitted(ANNA));
		assertTrue(state.isAdmitted(BEN));
		assertFalse(state.isAdmitted(CARA));
		assertEquals(1, state.position(CARA), "the one left over is now at the front");
	}

	@Test
	void aPlayerWhoDropsOutOfTheLineKeepsTheirPlaceAndThenLosesIt() {
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);

		state.markWaitingOffline(ANNA, 5000L);
		sweep(5000L + GRACE - 1L);

		assertEquals(1, state.position(ANNA), "still holding first place inside the window");
		assertEquals(BEN, state.nextOnline().uuid(), "but the next person through is the one who is here");

		sweep(5000L + GRACE);
		assertEquals(0, state.position(ANNA), "the window is up");
		assertEquals(1, state.position(BEN));
	}

	@Test
	void comingBackInsideTheWindowKeepsTheHeadOfTheLine() {
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);
		state.markWaitingOffline(ANNA, 5000L);

		state.enqueue(ANNA, "Anna", 5000L + GRACE - 1L);
		sweep(5000L + GRACE + 60_000L);

		assertEquals(1, state.position(ANNA), "reconnecting in time keeps the place, it does not renew it");
		assertEquals(ANNA, state.nextOnline().uuid());
	}

	@Test
	void anAdmittedPlayerWhoDropsOutHoldsTheirSlotAgainstTheCap() {
		state.setCap(1);
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);
		admitWhileThereIsRoom(3000L);

		assertTrue(state.isAdmitted(ANNA));
		assertEquals(1, state.position(BEN));

		state.markAdmittedOffline(ANNA, 4000L);
		assertEquals(0, admitWhileThereIsRoom(4000L), "Ben cannot have the slot Anna is still holding");

		sweep(4000L + GRACE);
		assertFalse(state.isAdmitted(ANNA));
		assertEquals(1, admitWhileThereIsRoom(4000L + GRACE), "now it is his");
		assertTrue(state.isAdmitted(BEN));
	}

	@Test
	void aKickTakesTheSlotBackStraightAway() {
		state.setCap(1);
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);
		admitWhileThereIsRoom(3000L);
		state.markAdmittedOffline(ANNA, 4000L);

		// What onKicked does: no waiting for the window.
		state.dequeue(ANNA);
		state.release(ANNA);

		assertEquals(1, admitWhileThereIsRoom(4000L));
		assertTrue(state.isAdmitted(BEN), "kicking somebody is supposed to free the slot now");
	}

	@Test
	void endingTheSessionClearsBothSidesOfIt() {
		state.setCap(4);
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);
		admitWhileThereIsRoom(3000L);
		state.enqueue(CARA, "Cara", 4000L);

		state.setQueueOpen(false);
		state.clearAdmitted();
		state.clearQueue();

		assertEquals(0, state.slotsUsed());
		assertEquals(0, state.queueSize());
		assertFalse(state.queueOpen());
	}

	@Test
	void thewholeSessionSurvivesBeingWrittenOutHalfwayThrough() {
		state.setCap(2);
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);
		state.enqueue(CARA, "Cara", 3000L);
		admitWhileThereIsRoom(4000L);
		state.markAdmittedOffline(BEN, 5000L);

		state.flush();
		state.load(directory.resolve("lobby.json"));

        assertTrue(state.isAdmitted(ANNA));
		assertTrue(state.isAdmitted(BEN));
		assertFalse(state.admittedEntry(BEN).online(), "and he is still on his grace window");
		assertEquals(1, state.position(CARA));
		assertEquals(2, state.cap());

		sweep(5000L + GRACE);
		assertFalse(state.isAdmitted(BEN), "the window carries on running across the restart");
	}

	@Test
	void nobodyCanHoldTwoPlacesAtOnce() {
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(ANNA, "Anna", 2000L);
		state.enqueue(ANNA, "Anna", 3000L);

		assertEquals(1, state.queueSize());

		List<Waiting> queue = state.queue();
		assertEquals(1, queue.size());
		assertEquals(1000L, queue.get(0).joinedAt(), "the place they took the first time is the one they keep");
	}

	@Test
	void aNameChangeDoesNotLoseSomebodysPlace() {
		state.enqueue(ANNA, "Anna", 1000L);
		state.enqueue(BEN, "Ben", 2000L);
		state.enqueue(ANNA, "AnnaRenamed", 3000L);

		assertEquals(1, state.position(ANNA));
		assertEquals(ANNA, state.knownByName("annarenamed"));
		assertNull(state.knownByName("Anna"));
		assertNotNull(state.waiting(ANNA));
	}
}
