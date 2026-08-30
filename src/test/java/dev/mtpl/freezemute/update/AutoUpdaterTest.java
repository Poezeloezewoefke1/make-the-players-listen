package dev.mtpl.freezemute.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The release tag becomes part of a file name in the mods folder, and it arrives from whatever
 * repository the config points at. Checking the jar is this mod says nothing about where it was
 * written to.
 */
class AutoUpdaterTest {
	@Test
	void ordinaryVersionsAreFine() {
		assertTrue(AutoUpdater.usableTag("1.14.0"));
		assertTrue(AutoUpdater.usableTag("2.0.0-rc1"));
		assertTrue(AutoUpdater.usableTag("1.0.0_build.7"));
	}

	@Test
	void aTagThatWouldWriteSomewhereElseIsRefused() {
		assertFalse(AutoUpdater.usableTag("../../../../etc/cron.d/x"), "that is not a version, it is a path");
		assertFalse(AutoUpdater.usableTag("1.0/../../evil"));
		assertFalse(AutoUpdater.usableTag(".."));
		assertFalse(AutoUpdater.usableTag("1..0"), "two dots are how you climb out of a folder");
		assertFalse(AutoUpdater.usableTag("C:\\windows\\x"));
	}

	@Test
	void nothingAtAllIsRefused() {
		assertFalse(AutoUpdater.usableTag(null));
		assertFalse(AutoUpdater.usableTag(""));
		assertFalse(AutoUpdater.usableTag(" 1.0 "), "a name with spaces in it is not one we chose");
	}
}
