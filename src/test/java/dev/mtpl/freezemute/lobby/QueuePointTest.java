package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whether there is somewhere to ask for a place decides how the queue is joined, so it is worth
 * pinning down that the two modes really are the two modes.
 */
class QueuePointTest {
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
	void withNoPointTheQueueIsJoinedByTurningUp() {
		assertNull(state.queuePoint());
		assertFalse(state.joinedAtAPoint());
	}

	@Test
	void settingAPointSwitchesToAskingForIt() {
		state.setQueuePoint(new Spot(10.5D, 65.0D, -16.5D, 0.0F, 0.0F));

		assertTrue(state.joinedAtAPoint());
		assertEquals(10.5D, state.queuePoint().x());
	}

	@Test
	void clearingItGoesBackToAutomatic() {
		state.setQueuePoint(new Spot(1.0D, 2.0D, 3.0D, 0.0F, 0.0F));
		state.setQueuePoint(null);

		assertFalse(state.joinedAtAPoint());
		assertNull(state.queuePoint());
	}

	@Test
	void thePointSurvivesARestart() {
		state.setQueuePoint(new Spot(0.5D, 65.0D, -16.5D, 90.0F, 0.0F));
		state.load(file);

		assertTrue(state.joinedAtAPoint());
		Spot point = state.queuePoint();
		assertNotNull(point);
		assertEquals(-16.5D, point.z());
		assertEquals(90.0F, point.yaw());
	}

	@Test
	void aClearedPointStaysClearedAcrossARestart() {
		state.setQueuePoint(new Spot(1.0D, 2.0D, 3.0D, 0.0F, 0.0F));
		state.setQueuePoint(null);
		state.load(file);

		assertFalse(state.joinedAtAPoint());
	}
}
