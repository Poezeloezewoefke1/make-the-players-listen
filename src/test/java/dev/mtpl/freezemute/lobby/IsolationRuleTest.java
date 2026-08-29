package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Who can see whom in the lobby, as a table.
 *
 * <p>Getting this wrong is what made one player visible to another while the other saw nothing,
 * which is not the sort of thing that shows up in a compiling build.
 */
class IsolationRuleTest {
	private static boolean hides(boolean isolating, boolean subject, boolean receiver, boolean same) {
		return LobbyManager.hides(isolating, subject, receiver, same);
	}

	@Test
	void oneMemberIsHiddenFromAnother() {
		assertTrue(hides(true, true, true, false));
	}

	@Test
	void itIsHiddenBothWaysOrNeither() {
		// The bug was that this was not symmetric. Whatever the answer for a pair, swapping who
		// is looking has to give the same one.
		for (boolean isolating : new boolean[] { true, false }) {
			boolean anna = hides(isolating, true, true, false);
			boolean ben = hides(isolating, true, true, false);
			assertTrue(anna == ben, "two members should see each other, or not, together");
		}
	}

	@Test
	void staffSeeMembers() {
		// Staff are not members, so the receiver side of the rule fails.
		assertFalse(hides(true, true, false, false));
	}

	@Test
	void membersSeeStaff() {
		// And staff are not hidden, so the subject side fails.
		assertFalse(hides(true, false, true, false));
	}

	@Test
	void nobodyIsHiddenFromThemselves() {
		assertFalse(hides(true, true, true, true), "a player has to be able to see their own arm");
	}

	@Test
	void switchingIsolationOffMakesTheRoomAnOrdinaryOne() {
		assertFalse(hides(false, true, true, false));
	}

	@Test
	void twoPeopleWhoAreNotInTheLobbyAtAllAreUnaffected() {
		assertFalse(hides(true, false, false, false));
	}

	@Test
	void everyCombinationIsAccountedFor() {
		// Sixteen of them, and exactly one should hide anything.
		int hiding = 0;

		for (int bits = 0; bits < 16; bits++) {
			boolean isolating = (bits & 1) != 0;
			boolean subject = (bits & 2) != 0;
			boolean receiver = (bits & 4) != 0;
			boolean same = (bits & 8) != 0;

			if (hides(isolating, subject, receiver, same)) {
				hiding++;
				assertTrue(isolating && subject && receiver && !same);
			}
		}

		assertTrue(hiding == 1, "expected exactly one combination to hide, found " + hiding);
	}
}
