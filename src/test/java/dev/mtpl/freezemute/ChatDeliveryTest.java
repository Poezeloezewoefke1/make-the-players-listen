package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Who hears whom, stated as a table rather than inferred from a mixin. */
class ChatDeliveryTest {
	@Test
	void anOrdinaryMessageReachesEverybody() {
		assertFalse(ChatDelivery.drops(false, false, false));
		assertFalse(ChatDelivery.drops(false, false, true));
	}

	@Test
	void aMutedPlayerIsSilentToStaffAsWell() {
		assertTrue(ChatDelivery.drops(true, false, false));
		assertTrue(ChatDelivery.drops(true, false, true),
				"a punishment somebody can talk their way around is not one");
	}

	@Test
	void theLobbyIsQuietButNotSoundproof() {
		assertTrue(ChatDelivery.drops(false, true, false), "the rest of the room does not hear it");
		assertFalse(ChatDelivery.drops(false, true, true),
				"the room tells people staff can hear them, so staff have to be able to");
	}

	@Test
	void aMutedPlayerInTheLobbyIsStillMuted() {
		assertTrue(ChatDelivery.drops(true, true, true),
				"waiting in line is not a way to get a mute lifted");
	}
}
