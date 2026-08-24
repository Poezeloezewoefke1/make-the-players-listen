package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import dev.mtpl.freezemute.ModerationData.FreezeEntry;
import dev.mtpl.freezemute.ModerationData.MuteEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModerationDataTest {
	private static final UUID STEVE = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID ALEX = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@TempDir
	Path directory;

	private Path file;
	private ModerationData data;

	@BeforeEach
	void setUp() {
		file = directory.resolve("moderation.json");
		data = ModerationData.get();
		data.load(file);
	}

	@Test
	void startsEmpty() {
		assertFalse(data.isFrozen(STEVE));
		assertFalse(data.isMuted(STEVE));
		assertTrue(data.frozenEntries().isEmpty());
		assertTrue(data.muteEntries().isEmpty());
	}

	@Test
	void freezesAndUnfreezes() {
		assertTrue(data.freeze(new FreezeEntry(STEVE, "Steve", "Console", System.currentTimeMillis())));
		assertTrue(data.isFrozen(STEVE));
		assertFalse(data.isFrozen(ALEX));

		assertNotNull(data.unfreeze(STEVE));
		assertFalse(data.isFrozen(STEVE));
		assertNull(data.unfreeze(STEVE));
	}

	@Test
	void timedMuteExpiresOnItsOwn() {
		long now = System.currentTimeMillis();
		data.mute(new MuteEntry(STEVE, "Steve", "Console", now, now + 60_000L, "spam"));
		assertTrue(data.isMuted(STEVE));

		// A mute whose end has passed is treated as gone, without anybody running /unmute.
		data.mute(new MuteEntry(ALEX, "Alex", "Console", now - 120_000L, now - 1L, ""));
		assertFalse(data.isMuted(ALEX));
		assertNull(data.muteOf(ALEX));
		assertEquals(1, data.muteEntries().size());
	}

	@Test
	void permanentMuteNeverExpires() {
		long now = System.currentTimeMillis();
		MuteEntry entry = new MuteEntry(STEVE, "Steve", "Console", now, 0L, "");
		data.mute(entry);

		assertTrue(entry.permanent());
		assertFalse(entry.expired(now + 10_000_000L));
		assertTrue(data.isMuted(STEVE));
	}

	@Test
	void survivesAReload() throws IOException {
		long now = System.currentTimeMillis();
		data.freeze(new FreezeEntry(STEVE, "Steve", "Console", now));
		data.mute(new MuteEntry(ALEX, "Alex", "Notch", now, now + 3_600_000L, "being rude"));

		assertTrue(Files.isRegularFile(file), "the state file should have been written");

		// Reload from disk, the way a server restart would.
		data.load(file);

		assertTrue(data.isFrozen(STEVE));
		MuteEntry mute = data.muteOf(ALEX);
		assertNotNull(mute);
		assertEquals("Alex", mute.name());
		assertEquals("Notch", mute.source());
		assertEquals("being rude", mute.reason());
		assertFalse(mute.permanent());
	}

	@Test
	void findsByNameIgnoringCaseAndFollowsRenames() {
		long now = System.currentTimeMillis();
		data.freeze(new FreezeEntry(STEVE, "Steve", "Console", now));
		data.mute(new MuteEntry(STEVE, "Steve", "Console", now, 0L, ""));

		assertNotNull(data.findFrozenByName("steve"));
		assertNotNull(data.findMutedByName("STEVE"));
		assertNull(data.findFrozenByName("Alex"));

		data.refreshName(STEVE, "SteveTheSecond");
		assertNotNull(data.findFrozenByName("stevethesecond"));
		assertNotNull(data.findMutedByName("SteveTheSecond"));
		assertNull(data.findFrozenByName("Steve"));
	}
}
