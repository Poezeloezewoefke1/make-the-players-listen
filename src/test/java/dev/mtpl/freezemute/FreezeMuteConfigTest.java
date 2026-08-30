package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reading settings, and - more to the point - not destroying them. */
class FreezeMuteConfigTest {
	@TempDir
	Path directory;

	/**
	 * The settings are one global, so a test that changes them changes them for whatever runs next.
	 * Every test here ends by putting the defaults back.
	 */
	@AfterEach
	void restoreTheDefaults() {
		FreezeMuteConfig.load(directory.resolve("defaults.json"));
	}

	@Test
	void settingsAreRead() throws Exception {
		Path file = directory.resolve("config.json");
		Files.writeString(file, "{ \"lobbyQueueGraceSeconds\": 42, \"lobbyIsolateMembers\": false }");

		FreezeMuteConfig.load(file);

		assertEquals(42, FreezeMuteConfig.get().lobbyQueueGraceSeconds);
		assertEquals(false, FreezeMuteConfig.get().lobbyIsolateMembers);
	}

	@Test
	void aFileThatWasReadIsWrittenBackWithSettingsAddedSince() throws Exception {
		Path file = directory.resolve("config.json");
		Files.writeString(file, "{ \"lobbyQueueGraceSeconds\": 42 }");

		FreezeMuteConfig.load(file);

		String written = Files.readString(file);
		assertTrue(written.contains("\"lobbyQueueGraceSeconds\": 42"), "the setting they chose survives");
		assertTrue(written.contains("lobbyVoidCatchY"), "and ones they have never seen appear with defaults");
	}

	@Test
	void aFileWithATypoInItIsLeftExactlyAsItIs() throws Exception {
		// One missing comma. Overwriting it with defaults would cost them every setting they had
		// and take away the text they need to see to find the mistake.
		String broken = "{ \"lobbyQueueGraceSeconds\": 42 \"lobbyIsolateMembers\": false }";
		Path file = directory.resolve("config.json");
		Files.writeString(file, broken);

		FreezeMuteConfig.load(file);

		assertEquals(broken, Files.readString(file), "their file was overwritten");
		assertEquals(300, FreezeMuteConfig.get().lobbyQueueGraceSeconds, "and the run uses defaults");
	}

	@Test
	void aFileThatIsNotAnObjectIsLeftAloneToo() throws Exception {
		Path file = directory.resolve("config.json");
		Files.writeString(file, "[ 1, 2, 3 ]");

		FreezeMuteConfig.load(file);

		assertEquals("[ 1, 2, 3 ]", Files.readString(file));
	}

	@Test
	void aMissingFileIsWrittenWithTheDefaults() throws Exception {
		Path file = directory.resolve("config.json");

		FreezeMuteConfig.load(file);

		assertTrue(Files.isRegularFile(file), "the point of the defaults is that they can be edited");
		assertTrue(Files.readString(file).contains("lobbyQueueGraceSeconds"));
	}
}
