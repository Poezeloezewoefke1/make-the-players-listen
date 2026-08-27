package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mtpl.freezemute.update.Versions;

import org.junit.jupiter.api.Test;

/**
 * The auto updater only replaces the running jar when the release really is newer, so getting
 * this wrong either means missing updates forever or reinstalling the same version every boot.
 */
class VersionsTest {
	@Test
	void newerVersionsWin() {
		assertTrue(Versions.compare("1.6.0", "1.5.0") > 0);
		assertTrue(Versions.compare("1.5.1", "1.5.0") > 0);
		assertTrue(Versions.compare("2.0.0", "1.99.99") > 0);
		assertTrue(Versions.compare("1.10.0", "1.9.0") > 0, "ten is bigger than nine, not smaller");
	}

	@Test
	void olderVersionsLose() {
		assertTrue(Versions.compare("1.5.0", "1.6.0") < 0);
		assertTrue(Versions.compare("1.4.9", "1.5.0") < 0);
	}

	@Test
	void theSameVersionIsNotAnUpdate() {
		assertEquals(0, Versions.compare("1.5.0", "1.5.0"));
		// A missing part is a zero, so these are the same release written two ways.
		assertEquals(0, Versions.compare("1.5", "1.5.0"));
		assertEquals(0, Versions.compare("1.5.0.0", "1.5"));
	}

	@Test
	void preReleasesLoseToTheRealThing() {
		assertTrue(Versions.compare("1.5.0-rc1", "1.5.0") < 0);
		assertTrue(Versions.compare("1.5.0", "1.5.0-rc1") > 0);
		assertEquals(0, Versions.compare("1.5.0-rc1", "1.5.0-rc2"), "both are pre-releases of the same version");
		assertTrue(Versions.compare("1.6.0-rc1", "1.5.0") > 0, "a pre-release of a newer version is still newer");
	}

	@Test
	void rubbishNeverCountsAsAnUpdate() {
		// If the version cannot be read it must not look newer than what is running, or the mod
		// would download the same jar on every single boot.
		assertTrue(Versions.compare("", "1.5.0") < 0);
		assertTrue(Versions.compare("not-a-version", "1.5.0") < 0);
		assertTrue(Versions.compare("1.5.0", "") > 0);
	}

	@Test
	void buildMetadataIsIgnored() {
		assertEquals(0, Versions.compare("1.5.0+fabric", "1.5.0"));
	}
}
