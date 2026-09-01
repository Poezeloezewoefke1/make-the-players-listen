package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
