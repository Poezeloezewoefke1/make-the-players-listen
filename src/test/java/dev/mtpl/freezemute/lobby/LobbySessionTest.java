package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import dev.mtpl.freezemute.FreezeMuteConfig;
import dev.mtpl.freezemute.lobby.FakeRoom.FakePlayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A whole evening in the lobby, made up at random, checked a second at a time.
 *
 * <p>The scenario tests next door each say "given this, the lobby should do that", and each of
 * them was written because somebody had already noticed the thing it pins. This one does not know
 * what it is looking for. It plays thousands of seconds of people arriving, wandering off,
 * crashing, coming back, being made operators and losing it again, with the cap and the doors
 * changing under them, and after every single second it asks the same handful of questions that
 * have to be true no matter what happened: nobody holds a place and a slot at once, the cap is a
 * cap, staff are in neither list, and the books add up.
 *
 * <p>Written down as a seed and a run length so a failure is reproducible: the seeds are fixed, so
 * this either passes for everybody or fails for everybody with the same evening to look at.
 */
class LobbySessionTest {
	/** Forty evenings of a thousand seconds each. Enough to turn up what a scenario would not,
	 * and quick enough that nobody is tempted to take it out again. */
	private static final int SEEDS = 40;
	private static final int SECONDS = 1000;
	/** The course the made-up runs are filed against. */
	private static final String COURSE = "spawn";

	@TempDir
	Path directory;

	private LobbyState state;
	private FreezeMuteConfig config;

	@BeforeEach
	void setUp() {
		state = LobbyState.get();
		state.load(directory.resolve("lobby.json"));
		FreezeMuteConfig.load(directory.resolve("config.json"));
		config = FreezeMuteConfig.get();
	}

	@Test
	void noEveningInTheLobbyEverBreaksItsOwnRules() {
		for (int seed = 0; seed < SEEDS; seed++) {
			state.load(directory.resolve("lobby-" + seed + ".json"));
			state.setEnabled(true);
			state.setCap(2 + seed % 4);
			FakeRoom room = new FakeRoom();
			List<FakePlayer> everybody = new ArrayList<>();
			Random random = new Random(seed * 7919L + 13L);
			long now = 1_700_000_000_000L;

			for (int second = 0; second < SECONDS; second++) {
				stir(room, everybody, random, now);
				int held = state.slotsUsed();
				LobbyRules.tickSecond(room, state, config, now);
				check(room, state, held, "seed " + seed + " second " + second);
				now += 1_000L + random.nextInt(3_000);
			}
		}
	}

	/** Whatever a roomful of people might do in the next second. */
	private void stir(FakeRoom room, List<FakePlayer> everybody, Random random, long now) {
		switch (random.nextInt(11)) {
			case 0 -> {
				if (everybody.size() < 9) {
					FakePlayer arriving = room.add("Player" + everybody.size());
					everybody.add(arriving);

					if (random.nextBoolean()) {
						arriving.standingInTheLobby();
					}

					if (random.nextInt(6) == 0) {
						arriving.staff(true);
					}
				}
			}
			case 1 -> pick(everybody, random, player -> player.staff(!player.staff()));
			case 2 -> pick(everybody, random, FakePlayer::wanderOff);
			case 3 -> pick(everybody, random, FakePlayer::standingInTheLobby);
			case 4 -> state.setCap(random.nextInt(5));
			case 5 -> state.setQueueOpen(random.nextBoolean());
			case 6 -> state.setQueuePoint(random.nextBoolean()
					? new Spot(0.5D, 65.0D, 0.5D, 0.0F, 0.0F) : null);
			case 7 -> {
				// Somebody's connection drops. The lobby only learns from the room going quiet.
				if (!everybody.isEmpty()) {
					FakePlayer leaving = everybody.remove(random.nextInt(everybody.size()));
					room.disconnect(leaving);
					state.markWaitingOffline(leaving.uuid(), now);
				}
			}
			case 8 -> room.built = random.nextInt(8) != 0;
			case 9 -> {
				// The rest of what the room remembers: who skips the line, where somebody was
				// standing before it swallowed them, and the times on the board.
				UUID who = new UUID(11L, random.nextInt(5));

				switch (random.nextInt(3)) {
					case 0 -> state.addEarlyAccess(who, "Early" + (who.getLeastSignificantBits()));
					case 1 -> state.rememberReturn(who, "minecraft:overworld",
							new Spot(random.nextInt(100), 64.0D, random.nextInt(100), 90.0F, 0.0F), "SURVIVAL");
					default -> state.recordTime(COURSE, new CourseRecord(who,
							"Runner" + who.getLeastSignificantBits(), 4_000L + random.nextInt(60_000), now));
				}
			}
			default -> {
				// A quiet second, which is most of them.
			}
		}
	}

	private void pick(List<FakePlayer> everybody, Random random, java.util.function.Consumer<FakePlayer> what) {
		if (!everybody.isEmpty()) {
			what.accept(everybody.get(random.nextInt(everybody.size())));
		}
	}

	@Test
	void anEveningSurvivesBeingWrittenDownAndReadBackAtAnyMoment() {
		// The room's whole memory goes through one file, and it is written while people are in the
		// middle of using it. A field that is written but not read back, or read back as null, is
		// invisible until the day somebody restarts the server at the wrong moment - and then the
		// line, the slots, or the course everybody has times on is simply gone.
		for (int seed = 0; seed < 8; seed++) {
			Path file = directory.resolve("saved-" + seed + ".json");
			state.load(file);
			state.setEnabled(true);
			state.setCap(2 + seed % 3);
			state.putCourse(LobbyBuilder.plan(seed * 400, 0, 53).course());
			FakeRoom room = new FakeRoom();
			List<FakePlayer> everybody = new ArrayList<>();
			Random random = new Random(seed * 104_729L + 7L);
			long now = 1_700_000_000_000L;

			for (int second = 0; second < 120; second++) {
				stir(room, everybody, random, now);
				LobbyRules.tickSecond(room, state, config, now);
				now += 1_000L;

				String before = snapshot(state);
				state.flush();
				assertFalse(state.pendingWrite(), "seed " + seed + " second " + second
						+ ": the room still has something it has not written down");
				state.load(file);

				assertEquals(before, snapshot(state), "seed " + seed + " second " + second
						+ ": the room came back from its own file different from how it went in");
			}
		}
	}

	/**
	 * Everything the room is supposed to remember, as one comparable line.
	 *
	 * <p>Spots go in whole rather than through describe(), which prints three numbers and drops
	 * the two angles. The spawn's yaw is what has somebody arriving looking at the pedestal
	 * instead of away from it, and three numbers would never have noticed it going missing.
	 */
	private static String snapshot(LobbyState state) {
		StringBuilder written = new StringBuilder();
		written.append("cap=").append(state.cap())
				.append(" on=").append(state.enabled())
				.append(" open=").append(state.queueOpen())
				.append(" spawn=").append(state.spawn())
				.append(" point=").append(state.joinedAtAPoint() ? state.queuePoint().toString() : "none");

		for (java.util.Map.Entry<UUID, String> early : new java.util.TreeMap<>(state.earlyAccess()).entrySet()) {
			written.append(" early:").append(early.getKey()).append('/').append(early.getValue());
		}

		for (LobbyState.Waiting entry : state.queue()) {
			written.append(" waiting:").append(entry.uuid()).append('/').append(entry.name());
		}

		for (LobbyState.Admitted entry : state.admitted()) {
			written.append(" slot:").append(entry.uuid()).append('/').append(entry.name());
		}

		for (int index = 0; index < 5; index++) {
			UUID who = new UUID(11L, index);
			written.append(" back:").append(who).append('/')
					.append(state.hasReturn(who) ? "somewhere" : "nowhere");
		}

		for (CourseRecord time : state.leaderboard(COURSE)) {
			written.append(" time:").append(time.uuid()).append('/').append(time.name())
					.append('/').append(time.millis()).append('/').append(time.at());
		}

		for (String course : new java.util.TreeSet<>(state.courseNames())) {
			Course laid = state.course(course);
			written.append(" course:").append(course).append('/').append(laid.checkpoints().size())
					.append('/').append(laid.start())
					.append('/').append(laid.playable() ? laid.finish().toString() : "unfinished");
		}

		return written.toString();
	}

	/**
	 * The things that have to be true after any second at all.
	 *
	 * @param held how many slots were out before this second, because the cap bounds who is let
	 *             in and not who is already in: lowering it under a roomful of people leaves them
	 *             where they are rather than throwing them out of a game they are in the middle of
	 */
	private void check(FakeRoom room, LobbyState state, int held, String where) {
		Set<UUID> queued = new HashSet<>();

		for (LobbyState.Waiting entry : state.queue()) {
			assertTrue(queued.add(entry.uuid()), where + ": " + entry.name() + " is in the line twice");
			assertFalse(state.isAdmitted(entry.uuid()),
					where + ": " + entry.name() + " is holding a place in line and a slot at once");
		}

		Set<UUID> holders = new HashSet<>();

		for (LobbyState.Admitted entry : state.admitted()) {
			assertTrue(holders.add(entry.uuid()), where + ": " + entry.name() + " holds two slots");
		}

		assertEquals(holders.size(), state.slotsUsed(),
				where + ": the slot count and the list of slot holders disagree");

		if (state.cap() > 0) {
			assertTrue(state.slotsUsed() <= Math.max(held, state.cap()), where + ": " + state.slotsUsed()
					+ " slots are held, the cap is " + state.cap() + " and only " + held
					+ " were out a second ago, so somebody was let in past the cap");
		}

		for (Occupant occupant : room.occupants()) {
			if (!occupant.staff()) {
				assertTrue(occupant.inLobby() || !occupant.member() || !room.built(),
						where + ": " + occupant.name() + " is held by a room they are not standing in");
				continue;
			}

			assertFalse(queued.contains(occupant.uuid()),
					where + ": " + occupant.name() + " is staff and still in the line");
			assertFalse(holders.contains(occupant.uuid()),
					where + ": " + occupant.name() + " is staff and still holding a slot");
			assertFalse(occupant.member(),
					where + ": " + occupant.name() + " is staff and still a member of the room");
		}
	}
}
