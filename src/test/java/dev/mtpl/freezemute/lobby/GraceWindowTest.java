package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import dev.mtpl.freezemute.lobby.LobbyState.Admitted;
import dev.mtpl.freezemute.lobby.LobbyState.Waiting;

import org.junit.jupiter.api.Test;

/**
 * The two grace windows.
 *
 * <p>They are the part of the queue people notice going wrong: a place lost while somebody was
 * reconnecting, or a slot still held by somebody who left half an hour ago. The rule itself is
 * one line, which is exactly why it is worth pinning down.
 */
class GraceWindowTest {
	private static final UUID STEVE = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final long FIVE_MINUTES = 300_000L;

	private static Waiting waiting(long offlineSince) {
		return new Waiting(STEVE, "Steve", 0L, offlineSince);
	}

	private static Admitted admitted(long offlineSince) {
		return new Admitted(STEVE, "Steve", 0L, offlineSince);
	}

	@Test
	void somebodyStillConnectedNeverRunsOut() {
		assertFalse(waiting(0L).graceRanOut(Long.MAX_VALUE, FIVE_MINUTES));
		assertFalse(admitted(0L).graceRanOut(Long.MAX_VALUE, FIVE_MINUTES));
	}

	@Test
	void thePlaceIsHeldForTheWholeWindow() {
		Waiting entry = waiting(1_000L);

		assertFalse(entry.graceRanOut(1_000L, FIVE_MINUTES), "gone for no time at all");
		assertFalse(entry.graceRanOut(1_000L + FIVE_MINUTES - 1L, FIVE_MINUTES), "one millisecond to go");
		assertTrue(entry.graceRanOut(1_000L + FIVE_MINUTES, FIVE_MINUTES), "the window is up");
		assertTrue(entry.graceRanOut(1_000L + FIVE_MINUTES * 10L, FIVE_MINUTES));
	}

	@Test
	void theSlotIsHeldOnTheSameTerms() {
		Admitted entry = admitted(1_000L);

		assertFalse(entry.graceRanOut(1_000L + FIVE_MINUTES - 1L, FIVE_MINUTES));
		assertTrue(entry.graceRanOut(1_000L + FIVE_MINUTES, FIVE_MINUTES));
	}

	@Test
	void aWindowOfNothingDropsThemAsSoonAsTheyGo() {
		// Setting the grace to zero should mean no grace, not an infinite one.
		assertTrue(waiting(1_000L).graceRanOut(1_000L, 0L));
		assertTrue(admitted(1_000L).graceRanOut(1_000L, 0L));
	}

	@Test
	void aClockThatGoesBackwardsDoesNotEvictAnybody() {
		// The system clock can step back. Losing your place because a time server corrected the
		// machine would be an odd way to be thrown out of a queue.
		assertFalse(waiting(10_000L).graceRanOut(9_000L, FIVE_MINUTES));
		assertFalse(admitted(10_000L).graceRanOut(9_000L, FIVE_MINUTES));
	}
}
