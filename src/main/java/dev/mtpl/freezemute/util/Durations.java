package dev.mtpl.freezemute.util;

import java.util.Locale;

/** Parsing and formatting of the durations used by {@code /mute}. */
public final class Durations {
	/** Value used for "never expires". */
	public static final long PERMANENT = 0L;
	/** Value returned when the text could not be parsed. */
	public static final long INVALID = -1L;

	private static final long SECOND = 1000L;
	private static final long MINUTE = 60L * SECOND;
	private static final long HOUR = 60L * MINUTE;
	private static final long DAY = 24L * HOUR;
	private static final long WEEK = 7L * DAY;

	private Durations() {
	}

	/**
	 * Parses durations such as {@code 30s}, {@code 10m}, {@code 2h}, {@code 7d}, {@code 1w} and
	 * combinations like {@code 1h30m}. A bare number is read as minutes, and
	 * {@code perm} / {@code permanent} / {@code forever} mean "never expires".
	 *
	 * @return the duration in milliseconds, {@link #PERMANENT} or {@link #INVALID}
	 */
	public static long parseMillis(String input) {
		String text = input.trim().toLowerCase(Locale.ROOT);

		if (text.isEmpty()) {
			return INVALID;
		}

		if (text.equals("perm") || text.equals("permanent") || text.equals("forever") || text.equals("inf")) {
			return PERMANENT;
		}

		long total = 0L;
		long number = -1L;
		boolean sawUnit = false;

		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);

			if (character >= '0' && character <= '9') {
				number = (number < 0L ? 0L : number) * 10L + (character - '0');

				if (number > 1_000_000L) {
					return INVALID;
				}

				continue;
			}

			if (number < 0L) {
				return INVALID;
			}

			long unit = switch (character) {
				case 's' -> SECOND;
				case 'm' -> MINUTE;
				case 'h' -> HOUR;
				case 'd' -> DAY;
				case 'w' -> WEEK;
				default -> INVALID;
			};

			if (unit == INVALID) {
				return INVALID;
			}

			total += number * unit;
			number = -1L;
			sawUnit = true;
		}

		if (number >= 0L) {
			// A trailing bare number means minutes: "/mute Steve 30" is 30 minutes.
			total += number * MINUTE;
			sawUnit = true;
		}

		if (!sawUnit || total <= 0L) {
			return INVALID;
		}

		return total;
	}

	/** Formats a duration as {@code 2d 3h 15m}. */
	public static String format(long millis) {
		if (millis <= 0L) {
			return "0s";
		}

		long remaining = millis;
		StringBuilder builder = new StringBuilder();

		remaining = append(builder, remaining, WEEK, "w");
		remaining = append(builder, remaining, DAY, "d");
		remaining = append(builder, remaining, HOUR, "h");
		remaining = append(builder, remaining, MINUTE, "m");

		long seconds = remaining / SECOND;

		if (seconds > 0L || builder.isEmpty()) {
			appendPart(builder, seconds, "s");
		}

		return builder.toString();
	}

	private static long append(StringBuilder builder, long remaining, long unit, String suffix) {
		long amount = remaining / unit;

		if (amount > 0L) {
			appendPart(builder, amount, suffix);
		}

		return remaining - amount * unit;
	}

	private static void appendPart(StringBuilder builder, long amount, String suffix) {
		if (!builder.isEmpty()) {
			builder.append(' ');
		}

		builder.append(amount).append(suffix);
	}
}
