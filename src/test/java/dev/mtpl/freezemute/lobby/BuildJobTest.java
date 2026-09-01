package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Laying an island a slice at a time.
 *
 * <p>What matters is that every block goes down exactly once, in the order the plan asked for -
 * later placements overwrite earlier ones, so a slice boundary in the wrong place would leave a
 * palm tree buried under the hill it was meant to sit on - and that the completion runs once,
 * after the last block rather than before it.
 */
class BuildJobTest {
	private static List<LobbyBuilder.Placement> blocks(int count) {
		List<LobbyBuilder.Placement> list = new ArrayList<>();

		for (int index = 0; index < count; index++) {
			list.add(new LobbyBuilder.Placement(index, 0, 0, Material.STONE));
		}

		return list;
	}

	@Test
	void everyBlockGoesDownOnceAndInOrder() {
		List<LobbyBuilder.Placement> plan = blocks(BuildJob.BLOCKS_PER_TICK * 2 + 137);
		List<Integer> laid = new ArrayList<>();
		BuildJob job = new BuildJob(null, plan, (world, placement) -> laid.add(placement.x()), null);

		int ticks = 0;

		while (!job.done()) {
			job.tick();
			ticks++;
			assertTrue(ticks < 100, "a build that never finishes is worse than one that hangs");
		}

		assertEquals(3, ticks, "two full slices and the remainder");
		assertEquals(plan.size(), laid.size(), "a block laid twice is a block that overwrote something");

		for (int index = 0; index < laid.size(); index++) {
			assertEquals(index, laid.get(index), "out of order at " + index + " - later placements must win");
		}
	}

	@Test
	void aTickNeverOverrunsItsSlice() {
		BuildJob job = new BuildJob(null, blocks(BuildJob.BLOCKS_PER_TICK * 3), (world, placement) -> { }, null);

		assertEquals(BuildJob.BLOCKS_PER_TICK, job.tick(), "the whole point is that it stops");
		assertEquals(BuildJob.BLOCKS_PER_TICK, job.placed());
		assertFalse(job.done());
	}

	@Test
	void theCompletionRunsOnceAndOnlyAfterTheLastBlock() {
		AtomicInteger finished = new AtomicInteger();
		List<String> events = new ArrayList<>();
		List<LobbyBuilder.Placement> plan = blocks(BuildJob.BLOCKS_PER_TICK + 1);

		BuildJob job = new BuildJob(null, plan, (world, placement) -> events.add("block"), () -> {
			finished.incrementAndGet();
			events.add("done");
		});

		job.tick();
		assertEquals(0, finished.get(), "there are still blocks to lay");

		job.tick();
		assertTrue(job.done());
		assertEquals(0, finished.get(), "ticking lays blocks; finishing is the caller's call");

		job.finish();
		job.finish();
		job.finish();

		assertEquals(1, finished.get(), "moving everybody onto the new spawn three times is three teleports");
		assertEquals("done", events.get(events.size() - 1), "the completion has to come after the last block");
	}

	@Test
	void progressIsSomethingAPersonCanRead() {
		BuildJob job = new BuildJob(null, blocks(BuildJob.BLOCKS_PER_TICK * 4), (world, placement) -> { }, null);

		assertEquals(0, job.percent());
		job.tick();
		assertEquals(25, job.percent());
		job.tick();
		assertEquals(50, job.percent());
	}

	@Test
	void anEmptyPlanIsFinishedRatherThanStuck() {
		BuildJob job = new BuildJob(null, List.of(), (world, placement) -> { }, null);

		assertTrue(job.done());
		assertEquals(100, job.percent(), "nothing to do is done, not nought per cent for ever");
		assertEquals(0, job.tick());
	}
}
