package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;

import dev.mtpl.freezemute.ModerationData.FreezeEntry;
import dev.mtpl.freezemute.ModerationData.MuteEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A long shift of freezing and muting, made up at random and checked after every step.
 *
 * <p>The scenario tests next door each pin something somebody already knew to look for. This one
 * does not know what it is looking for: it freezes, unfreezes, mutes, unmutes, renames, sweeps and
 * lets the clock run, in whatever order the seed says, and after every single change it asks the
 * same three questions. Is anything still listed that has run out? Do the two ways of asking
 * whether somebody is frozen agree? And does the whole book come back out of its own file exactly
 * as it went in - because that file is written while people are still using it, and a field that
 * is saved but never read back is invisible until the day somebody restarts the server.
 */
class ModerationSessionTest {
	private static final int SHIFTS = 10;
	private static final int STEPS = 300;
	private static final String[] NAMES = { "Anna", "Ben", "Cato", "Dee", "Eve", "Finn" };

	@TempDir
	Path directory;

	private ModerationData data;

	@BeforeEach
	void setUp() {
		data = ModerationData.get();
		data.load(directory.resolve("moderation.json"));
	}

	@Test
	void noShiftEverLeavesTheBookWrong() {
		for (int shift = 0; shift < SHIFTS; shift++) {
			Path file = directory.resolve("shift-" + shift + ".json");
			data.load(file);
			Random random = new Random(shift * 15_485_863L + 3L);
			List<UUID> people = new ArrayList<>();

			for (int index = 0; index < NAMES.length; index++) {
				people.add(new UUID(7L, index));
			}

			for (int step = 0; step < STEPS; step++) {
				// The real clock, because the book itself reads the real clock when it decides
				// what has run out. A made-up one would have put every timed entry in the past
				// and quietly tested nothing but the permanent ones.
				long now = System.currentTimeMillis();
				UUID who = people.get(random.nextInt(people.size()));
				String name = NAMES[people.indexOf(who)];
				String where = "shift " + shift + " step " + step;

				switch (random.nextInt(8)) {
					case 0 -> data.freeze(new FreezeEntry(who, name, "Staff", now, until(random, now),
							"because", random.nextBoolean()));
					case 1 -> data.unfreeze(who);
					case 2 -> data.mute(new MuteEntry(who, name, "Staff", now, until(random, now), "because"));
					case 3 -> data.unmute(who);
					case 4 -> data.sweepExpired();
					case 5 -> data.refreshName(who, name + "TheSecond");
					case 6 -> {
						if (random.nextInt(20) == 0) {
							data.clearFrozen();
						}
					}
					default -> {
						// Just the clock moving on, which is most of what happens.
					}
				}

				check(now, where);
				assertFalse(data.isFrozen(new UUID(7L, 99L)), where + ": somebody nobody touched is frozen");

				String before = snapshot();
				data.save();
				data.load(file);
				assertEquals(before, snapshot(), where + ": the book came back from its own file different");
			}
		}
	}

	/**
	 * When a freeze or a mute runs out: never, in a minute, or a second ago.
	 *
	 * <p>The last of those is the one worth having. An entry that has already run out is the
	 * thing every list, every lookup and every save is supposed to step over, and it is also the
	 * thing a careless one hands back.
	 */
	private static long until(Random random, long now) {
		return switch (random.nextInt(3)) {
			case 0 -> 0L;
			case 1 -> now + 60_000L;
			default -> now - 1_000L;
		};
	}

	/** The two questions that have to hold whatever was just done. */
	private void check(long now, String where) {
		for (FreezeEntry entry : data.frozenEntries()) {
			assertFalse(entry.expired(now),
					where + ": " + entry.name() + " is still listed as frozen and their time ran out");
			assertTrue(data.isFrozen(entry.uuid()),
					where + ": " + entry.name() + " is in the list of frozen players and is not frozen");
		}

		for (MuteEntry entry : data.muteEntries()) {
			assertFalse(entry.expired(now),
					where + ": " + entry.name() + " is still listed as muted and their time ran out");
			assertTrue(data.isMuted(entry.uuid()),
					where + ": " + entry.name() + " is in the list of muted players and is not muted");
		}
	}

	/** Everything the book is supposed to remember, as one comparable line. */
	private String snapshot() {
		TreeSet<String> lines = new TreeSet<>();

		for (FreezeEntry entry : data.frozenEntries()) {
			lines.add("frozen " + entry.uuid() + " " + entry.name() + " " + entry.source() + " " + entry.since()
					+ " " + entry.until() + " " + entry.reason() + " " + entry.wasInvulnerable());
		}

		for (MuteEntry entry : data.muteEntries()) {
			lines.add("muted " + entry.uuid() + " " + entry.name() + " " + entry.source() + " " + entry.since()
					+ " " + entry.until() + " " + entry.reason());
		}

		return String.join("\n", lines);
	}
}
