package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;

import dev.mtpl.freezemute.voice.VoiceData;
import dev.mtpl.freezemute.voice.VoiceData.Kind;
import dev.mtpl.freezemute.voice.VoiceData.VoiceEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The same random shift, pointed at the voice chat book.
 *
 * <p>Two lists that must never be mistaken for one another - a muted player can still hear, a
 * deafened one can still talk - kept in one file, with timed entries in both. The scenario tests
 * pin the cases somebody thought of; this asks after every change whether anything that ran out is
 * still listed, whether the two lists have leaked into each other, and whether the file gives back
 * what it was handed.
 */
class VoiceSessionTest {
	private static final int SHIFTS = 10;
	private static final int STEPS = 300;
	private static final String[] NAMES = { "Anna", "Ben", "Cato", "Dee", "Eve" };

	@TempDir
	Path directory;

	private VoiceData data;

	@BeforeEach
	void setUp() {
		data = VoiceData.get();
		data.load(directory.resolve("voice.json"));
	}

	@Test
	void noShiftEverLeavesTheVoiceBookWrong() {
		for (int shift = 0; shift < SHIFTS; shift++) {
			Path file = directory.resolve("voice-" + shift + ".json");
			data.load(file);
			Random random = new Random(shift * 32_452_843L + 11L);

			for (int step = 0; step < STEPS; step++) {
				long now = System.currentTimeMillis();
				int index = random.nextInt(NAMES.length);
				UUID who = new UUID(9L, index);
				Kind kind = random.nextBoolean() ? Kind.MUTE : Kind.DEAFEN;
				String where = "shift " + shift + " step " + step;

				switch (random.nextInt(6)) {
					case 0, 1 -> data.apply(kind, new VoiceEntry(who, NAMES[index], "Staff", now,
							until(random, now), "because"));
					case 2 -> data.lift(kind, who);
					case 3 -> data.sweepExpired();
					case 4 -> {
						if (random.nextInt(20) == 0) {
							data.clear(kind);
						}
					}
					default -> {
						// Nothing happening, which is most of a shift.
					}
				}

				check(now, where);

				String before = snapshot();
				data.save();
				data.load(file);
				assertEquals(before, snapshot(), where + ": the book came back from its own file different");
			}
		}
	}

	/** Never, in a minute, or a second ago - the last being the one every list has to step over. */
	private static long until(Random random, long now) {
		return switch (random.nextInt(3)) {
			case 0 -> 0L;
			case 1 -> now + 60_000L;
			default -> now - 1_000L;
		};
	}

	private void check(long now, String where) {
		for (Kind kind : Kind.values()) {
			for (VoiceEntry entry : data.entries(kind)) {
				assertFalse(entry.expired(now),
						where + ": " + entry.name() + " is still listed under " + kind + " and their time ran out");
				assertEquals(entry, data.entryOf(kind, entry.uuid()),
						where + ": the list and the lookup disagree about " + entry.name());
			}
		}

		for (VoiceEntry entry : data.entries(Kind.MUTE)) {
			assertTrue(data.isMuted(entry.uuid()), where + ": " + entry.name() + " is listed as muted and is not");
		}

		for (VoiceEntry entry : data.entries(Kind.DEAFEN)) {
			assertTrue(data.isDeafened(entry.uuid()),
					where + ": " + entry.name() + " is listed as deafened and is not");
		}

		// One direction only, and it is the one that matters. The short cut is read on every voice
		// packet, so it looks at the maps rather than at the clock: an entry that has run out but
		// has not been swept yet keeps it saying "somebody is punished", which costs a lookup
		// nobody needed for up to a second. Saying "nobody is punished" while somebody still is
		// would let a muted player talk, and that must never happen.
		if (data.isEmpty()) {
			assertTrue(data.entries(Kind.MUTE).isEmpty() && data.entries(Kind.DEAFEN).isEmpty(),
					where + ": the short cut says the server is quiet and somebody is still punished");
		}
	}

	private String snapshot() {
		TreeSet<String> lines = new TreeSet<>();

		for (Kind kind : Kind.values()) {
			for (VoiceEntry entry : data.entries(kind)) {
				lines.add(kind + " " + entry.uuid() + " " + entry.name() + " " + entry.source() + " "
						+ entry.since() + " " + entry.until() + " " + entry.reason());
			}
		}

		return String.join("\n", lines);
	}
}
