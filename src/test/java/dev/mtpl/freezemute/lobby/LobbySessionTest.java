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
		switch (random.nextInt(10)) {
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
