package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CourseRecordTest {
	@Test
	void underAMinuteIsSecondsAndHundredths() {
		assertEquals("0.00", CourseRecord.format(0L));
		assertEquals("1.50", CourseRecord.format(1500L));
		assertEquals("59.99", CourseRecord.format(59_999L));
	}

	@Test
	void aMinuteAndOverGetsTheMinutesInFront() {
		assertEquals("1:00.00", CourseRecord.format(60_000L));
		assertEquals("1:02.35", CourseRecord.format(62_350L));
		assertEquals("12:00.00", CourseRecord.format(720_000L));
	}

	@Test
	void aNegativeTimeIsTreatedAsZeroRatherThanPrinted() {
		assertEquals("0.00", CourseRecord.format(-5L));
	}
}
