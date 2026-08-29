package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cap, and the question the queue asks before it makes anybody wait.
 *
 * <p>Getting this wrong is what made the lobby teleport people in and straight back out: with no
 * cap set there is nothing to wait for, so anybody sent to the lobby was let out a second later.
 */
class LobbyCapacityTest {
	private static final UUID STEVE = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID ALEX = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@TempDir
	Path directory;

	private LobbyState state;

	@BeforeEach
	void setUp() {
		state = LobbyState.get();
		state.load(directory.resolve("lobby.json"));
	}

	@Test
	void noCapMeansThereIsAlwaysRoom() {
		assertTrue(LobbyManager.hasFreeSlot(state));

		state.admit(STEVE, "Steve", 1000L);
		state.admit(ALEX, "Alex", 2000L);

		assertTrue(LobbyManager.hasFreeSlot(state), "a cap of 0 is no cap at all");
	}

	@Test
	void aCapFillsUp() {
		state.setCap(2);
		assertTrue(LobbyManager.hasFreeSlot(state));

		state.admit(STEVE, "Steve", 1000L);
		assertTrue(LobbyManager.hasFreeSlot(state));

		state.admit(ALEX, "Alex", 2000L);
		assertFalse(LobbyManager.hasFreeSlot(state), "two of two slots used leaves no room");
	}

	@Test
	void aSlotHandedBackMakesRoomAgain() {
		state.setCap(1);
		state.admit(STEVE, "Steve", 1000L);
		assertFalse(LobbyManager.hasFreeSlot(state));

		state.release(STEVE);
		assertTrue(LobbyManager.hasFreeSlot(state));
	}

	@Test
	void anOfflinePlayerInsideTheirGraceStillFillsTheSlot() {
		state.setCap(1);
		state.admit(STEVE, "Steve", 1000L);
		state.markAdmittedOffline(STEVE, 2000L);

		assertFalse(LobbyManager.hasFreeSlot(state),
				"a held slot is a used slot, or the grace window would mean nothing");
	}
}
