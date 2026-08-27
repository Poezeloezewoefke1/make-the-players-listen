package dev.mtpl.freezemute.update;

/**
 * Comparing two mod versions.
 *
 * <p>Only enough to answer "is the release newer than what is running": the dotted numbers are
 * compared one at a time, a missing part counts as zero so 1.5 and 1.5.0 are the same version,
 * and anything after a dash marks a pre-release, which loses to the plain version it hangs off.
 */
public final class Versions {
	private Versions() {
	}

	/** Negative when {@code left} is older, zero when they match, positive when it is newer. */
	public static int compare(String left, String right) {
		String[] leftParts = numbers(left);
		String[] rightParts = numbers(right);
		int length = Math.max(leftParts.length, rightParts.length);

		for (int index = 0; index < length; index++) {
			int comparison = Long.compare(part(leftParts, index), part(rightParts, index));

			if (comparison != 0) {
				return comparison;
			}
		}

		// Same numbers: a plain version beats a pre-release of it, e.g. 1.5.0 over 1.5.0-rc1.
		boolean leftPre = isPreRelease(left);
		boolean rightPre = isPreRelease(right);

		if (leftPre == rightPre) {
			return 0;
		}

		return leftPre ? -1 : 1;
	}

	private static String[] numbers(String version) {
		String trimmed = version == null ? "" : version.trim();
		int dash = trimmed.indexOf('-');

		if (dash >= 0) {
			trimmed = trimmed.substring(0, dash);
		}

		int plus = trimmed.indexOf('+');

		if (plus >= 0) {
			trimmed = trimmed.substring(0, plus);
		}

		return trimmed.isEmpty() ? new String[0] : trimmed.split("\\.");
	}

	private static long part(String[] parts, int index) {
		if (index >= parts.length) {
			return 0L;
		}

		StringBuilder digits = new StringBuilder();

		for (char character : parts[index].toCharArray()) {
			if (character < '0' || character > '9') {
				break;
			}

			digits.append(character);
		}

		if (digits.isEmpty()) {
			return 0L;
		}

		try {
			return Long.parseLong(digits.toString());
		} catch (NumberFormatException exception) {
			return 0L;
		}
	}

	private static boolean isPreRelease(String version) {
		return version != null && version.indexOf('-') >= 0;
	}
}
