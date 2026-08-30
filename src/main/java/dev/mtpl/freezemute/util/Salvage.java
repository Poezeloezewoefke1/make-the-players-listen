package dev.mtpl.freezemute.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import dev.mtpl.freezemute.FreezeMute;

/**
 * What to do with a file the mod could not read.
 *
 * <p>Every store here starts from empty when its file will not parse, and writes itself back the
 * next time anything changes. Left where it is, the unreadable file is quietly replaced by an
 * empty one the first time somebody is muted - and every freeze, every mute and every place in
 * line that was in it is gone, with nothing left to recover them from and no sign it happened.
 *
 * <p>So it is moved aside first, under a name that says what it is and when. Whatever went wrong -
 * a half written file after a power cut, an edit with a comma missing, a disk that returned
 * rubbish - somebody can look at it afterwards and get their data back out by hand.
 */
public final class Salvage {
	private static final DateTimeFormatter STAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

	private Salvage() {
	}

	/**
	 * Moves an unreadable file out of the way of the empty one that is about to replace it.
	 *
	 * @return where it went, or null if it could not be moved or was not there
	 */
	public static Path setAside(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return null;
		}

		Path aside = file.resolveSibling(file.getFileName() + ".unreadable-" + STAMP.format(Instant.now()));

		try {
			Files.move(file, aside);
			FreezeMute.LOGGER.error("Kept the unreadable {} as {}. The mod is starting from empty, and would "
					+ "otherwise have written an empty file straight over it.", file, aside);
			return aside;
		} catch (IOException exception) {
			FreezeMute.LOGGER.error("Could not move the unreadable {} out of the way. Copy it somewhere safe "
					+ "before anything changes, or its contents will be overwritten.", file, exception);
			return null;
		}
	}
}
