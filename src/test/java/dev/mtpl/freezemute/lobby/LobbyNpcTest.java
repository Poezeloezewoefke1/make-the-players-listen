package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What the figure on the pedestal says. */
class LobbyNpcTest {
	@Test
	void itSaysWhatTheConfigSays() {
		assertEquals("Join the stream!", LobbyNpc.signText("Join the stream!"));
		assertEquals("Wachten aub", LobbyNpc.signText("Wachten aub"));
	}

	@Test
	void anEmptySettingFallsBackRatherThanLeavingItBlank() {
		// A nameless armour stand has no floating text at all, so a config somebody cleared out
		// would leave a figure on a pedestal with nothing saying what to do with it.
		assertEquals(LobbyNpc.DEFAULT_SIGN, LobbyNpc.signText(null));
		assertEquals(LobbyNpc.DEFAULT_SIGN, LobbyNpc.signText(""));
		assertEquals(LobbyNpc.DEFAULT_SIGN, LobbyNpc.signText("   "));
	}

	@Test
	void theDefaultIsTheOneThatWasAskedFor() {
		assertEquals("Join record!", LobbyNpc.DEFAULT_SIGN);
	}
	@Test
	void noneMeansABareStandWhicheverSlotItIsAskedFor() {
		// "none" is documented for the head. Somebody who tries it for the armour used to get
		// three lines in the log about none_chestplate, none_leggings and none_boots - items no
		// version of Minecraft has - because the material had the piece stuck on the end of it
		// before anything looked at what it said.
		for (String nothing : new String[] { "none", "NONE", " none ", "", "  ", null }) {
			assertTrue(LobbyNpc.wearsNothing(nothing), "'" + nothing + "' should mean a bare stand");
		}

		for (String something : new String[] { "diamond", "iron", "minecraft:lantern", " dragon_head " }) {
			assertFalse(LobbyNpc.wearsNothing(something), "'" + something + "' names something to wear");
		}
	}
}
