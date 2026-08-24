package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mtpl.freezemute.util.Durations;

import org.junit.jupiter.api.Test;

class DurationsTest {
	@Test
	void parsesSingleUnits() {
		assertEquals(30_000L, Durations.parseMillis("30s"));
		assertEquals(600_000L, Durations.parseMillis("10m"));
		assertEquals(7_200_000L, Durations.parseMillis("2h"));
		assertEquals(604_800_000L, Durations.parseMillis("7d"));
		assertEquals(604_800_000L, Durations.parseMillis("1w"));
	}

	@Test
	void parsesCombinedUnitsAndBareMinutes() {
		assertEquals(5_400_000L, Durations.parseMillis("1h30m"));
		assertEquals(90_000L, Durations.parseMillis("1m30s"));
		// A bare number means minutes, so "/mute Steve 30" is half an hour.
		assertEquals(1_800_000L, Durations.parseMillis("30"));
	}

	@Test
	void parsesPermanentSpellings() {
		assertEquals(Durations.PERMANENT, Durations.parseMillis("perm"));
		assertEquals(Durations.PERMANENT, Durations.parseMillis("permanent"));
		assertEquals(Durations.PERMANENT, Durations.parseMillis("forever"));
		assertEquals(Durations.PERMANENT, Durations.parseMillis("PERM"));
	}

	@Test
	void rejectsNonsense() {
		assertEquals(Durations.INVALID, Durations.parseMillis(""));
		assertEquals(Durations.INVALID, Durations.parseMillis("   "));
		assertEquals(Durations.INVALID, Durations.parseMillis("spamming"));
		assertEquals(Durations.INVALID, Durations.parseMillis("10x"));
		assertEquals(Durations.INVALID, Durations.parseMillis("m"));
		assertEquals(Durations.INVALID, Durations.parseMillis("-5m"));
		// A zero length mute would be pointless, and is almost certainly a typo.
		assertEquals(Durations.INVALID, Durations.parseMillis("0s"));
	}

	@Test
	void formatsReadableDurations() {
		assertEquals("0s", Durations.format(0L));
		assertEquals("45s", Durations.format(45_000L));
		assertEquals("1m 30s", Durations.format(90_000L));
		assertEquals("1h", Durations.format(3_600_000L));
		assertEquals("1d 1h", Durations.format(90_000_000L));
	}
}
