package dev.mtpl.freezemute.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Keeping a file the mod could not read, so the empty one replacing it does not take its place. */
class SalvageTest {
	@TempDir
	Path directory;

	@Test
	void theContentsAreKeptSomewhereTheNextWriteWillNotReach() throws Exception {
		Path file = directory.resolve("moderation.json");
		Files.writeString(file, "{ every freeze anybody ever set, with a comma missing }");

		Path aside = Salvage.setAside(file);

		assertNotNull(aside, "there was a file and it could be moved");
		assertFalse(Files.exists(file), "it is out of the way of the empty one about to be written");
		assertEquals("{ every freeze anybody ever set, with a comma missing }", Files.readString(aside));
		assertTrue(aside.getFileName().toString().startsWith("moderation.json.unreadable-"),
				"the name has to say what it is, or nobody will know to look at it");
	}

	@Test
	void aFileThatIsNotThereIsNotAProblem() {
		assertNull(Salvage.setAside(directory.resolve("never-existed.json")));
		assertNull(Salvage.setAside(null));
	}

	@Test
	void aDirectoryIsNotMistakenForAFile() throws Exception {
		Path folder = directory.resolve("folder");
		Files.createDirectory(folder);

		assertNull(Salvage.setAside(folder));
		assertTrue(Files.isDirectory(folder), "and it is still there");
	}

	@Test
	void twoFilesSetAsideDoNotLandOnTopOfEachOther() throws Exception {
		Path first = directory.resolve("a.json");
		Path second = directory.resolve("b.json");
		Files.writeString(first, "first");
		Files.writeString(second, "second");

		assertEquals("first", Files.readString(Salvage.setAside(first)));
		assertEquals("second", Files.readString(Salvage.setAside(second)));
	}
}
