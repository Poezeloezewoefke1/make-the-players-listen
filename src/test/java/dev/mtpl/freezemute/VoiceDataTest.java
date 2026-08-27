package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import dev.mtpl.freezemute.voice.VoiceData;
import dev.mtpl.freezemute.voice.VoiceData.Kind;
import dev.mtpl.freezemute.voice.VoiceData.VoiceEntry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoiceDataTest {
	private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

	@TempDir
	Path directory;

	private VoiceData data;
	private Path file;

	@BeforeEach
	void setUp() {
		data = VoiceData.get();
		file = directory.resolve("voice.json");
		data.load(file);
	}

	@AfterEach
	void tearDown() {
		data.clear(Kind.MUTE);
		data.clear(Kind.DEAFEN);
	}

	@Test
	void aMuteWithNoDurationLastsUntilItIsLifted() {
		data.apply(Kind.MUTE, permanent(STEVE, "Steve"));

		assertTrue(data.isMuted(STEVE));
		assertTrue(data.entryOf(Kind.MUTE, STEVE).permanent());
		assertFalse(data.isDeafened(STEVE), "muting should not deafen as well");
	}

	@Test
	void aTimedMuteLetsGoOnItsOwn() {
		long past = System.currentTimeMillis() - 1_000L;
		data.apply(Kind.MUTE, new VoiceEntry(STEVE, "Steve", "console", past - 1_000L, past, ""));

		assertFalse(data.isMuted(STEVE), "the mute ran out a second ago");
		assertNull(data.entryOf(Kind.MUTE, STEVE));
		assertTrue(data.entries(Kind.MUTE).isEmpty());
	}

	@Test
	void muteAndDeafenAreSeparate() {
		data.apply(Kind.MUTE, permanent(STEVE, "Steve"));
		data.apply(Kind.DEAFEN, permanent(ALEX, "Alex"));

		assertTrue(data.isMuted(STEVE));
		assertFalse(data.isDeafened(STEVE));
		assertTrue(data.isDeafened(ALEX));
		assertFalse(data.isMuted(ALEX));

		data.lift(Kind.MUTE, STEVE);
		assertFalse(data.isMuted(STEVE));
		assertTrue(data.isDeafened(ALEX), "lifting a mute should leave the deafen alone");
	}

	@Test
	void theHotPathShortCircuitsWhenNobodyIsPunished() {
		// The plugin checks this for every audio packet, so it has to be true when it should be.
		assertTrue(data.isEmpty());
		data.apply(Kind.MUTE, permanent(STEVE, "Steve"));
		assertFalse(data.isEmpty());
		data.lift(Kind.MUTE, STEVE);
		assertTrue(data.isEmpty());
	}

	@Test
	void punishmentsSurviveARestart() throws Exception {
		data.apply(Kind.MUTE, permanent(STEVE, "Steve"));
		data.apply(Kind.DEAFEN, permanent(ALEX, "Alex"));

		assertTrue(Files.isRegularFile(file), "the state should be on disk straight away");

		// A restart is exactly this: load the same file from scratch.
		data.load(file);

		assertTrue(data.isMuted(STEVE), "a restart should not hand the microphone back");
		assertTrue(data.isDeafened(ALEX));
	}

	@Test
	void liftingReportsWhetherThereWasAnythingToLift() {
		assertNull(data.lift(Kind.MUTE, STEVE), "nothing was in place");

		data.apply(Kind.MUTE, permanent(STEVE, "Steve"));
		assertNotNull(data.lift(Kind.MUTE, STEVE));
		assertNull(data.lift(Kind.MUTE, STEVE), "it was already lifted");
	}

	@Test
	void namesAreFoundCaseInsensitivelyAndFollowRenames() {
		data.apply(Kind.MUTE, permanent(STEVE, "Steve"));

		assertNotNull(data.findByName(Kind.MUTE, "steve"));
		assertNotNull(data.findByName(Kind.MUTE, "STEVE"));

		data.refreshName(STEVE, "SteveTheSecond");

		assertNull(data.findByName(Kind.MUTE, "Steve"), "the old name is gone");
		assertNotNull(data.findByName(Kind.MUTE, "stevethesecond"));
		assertTrue(data.isMuted(STEVE), "a rename must not lift the mute");
	}

	@Test
	void applyReportsWhetherItWasAlreadyInPlace() {
		assertTrue(data.apply(Kind.MUTE, permanent(STEVE, "Steve")));
		assertFalse(data.apply(Kind.MUTE, permanent(STEVE, "Steve")), "the second one is an update");
	}

	@Test
	void clearingOnlyTouchesOneList() {
		data.apply(Kind.MUTE, permanent(STEVE, "Steve"));
		data.apply(Kind.DEAFEN, permanent(ALEX, "Alex"));

		assertEquals(1, data.clear(Kind.MUTE));
		assertTrue(data.entries(Kind.MUTE).isEmpty());
		assertEquals(1, data.entries(Kind.DEAFEN).size());
	}

	private static VoiceEntry permanent(UUID uuid, String name) {
		return new VoiceEntry(uuid, name, "console", System.currentTimeMillis(), 0L, "");
	}
}
