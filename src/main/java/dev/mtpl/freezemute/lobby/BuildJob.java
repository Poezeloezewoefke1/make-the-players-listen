package dev.mtpl.freezemute.lobby;

import java.util.List;

import dev.mtpl.freezemute.FreezeMute;

/**
 * An island being laid, a few thousand blocks at a time.
 *
 * <p>A town-sized island is several hundred thousand blocks, and every one of them is a
 * {@code setBlockState} that relights the column and pokes its neighbours. Done in one go that is
 * tens of seconds with the server thread held the whole way: no ticks, no packets, and every
 * player timing out while a "cannot keep up" warning scrolls past. From the outside it looks
 * exactly like a crash, which is a poor thing for a command to look like.
 *
 * <p>So the plan is worked out in full up front - it is only a list, and cheap - and then applied
 * a slice per tick. The server keeps ticking throughout, and the people watching see the island
 * grow instead of seeing nothing at all.
 *
 * <p>How big a slice is the whole trade-off. Too small and a build crawls; too big and each tick
 * overruns its 50ms and the server stutters anyway. {@link #BLOCKS_PER_TICK} is set where a slice
 * is comfortably inside a tick on the kind of hardware a small server runs on.
 */
public final class BuildJob {
	/**
	 * Blocks laid per tick.
	 *
	 * <p>Eight thousand plain block writes is a few milliseconds even on a slow machine, which
	 * leaves the rest of the tick for the server to be a server. At this rate a quarter of a
	 * million blocks is about a second and a half of wall clock.
	 */
	public static final int BLOCKS_PER_TICK = 8_000;

	private final Object world;
	private final List<LobbyBuilder.Placement> placements;
	private final Placer placer;
	private final Runnable whenDone;
	private int next;
	private boolean finished;

	/** How a placement reaches a world. Handed in so the job itself knows nothing about Minecraft. */
	@FunctionalInterface
	public interface Placer {
		void place(Object world, LobbyBuilder.Placement placement);
	}

	public BuildJob(Object world, List<LobbyBuilder.Placement> placements, Placer placer, Runnable whenDone) {
		this.world = world;
		this.placements = placements;
		this.placer = placer;
		this.whenDone = whenDone;
	}

	public boolean done() {
		return next >= placements.size();
	}

	public int total() {
		return placements.size();
	}

	public int placed() {
		return Math.min(next, placements.size());
	}

	/** 0 to 100, for something a person is watching. */
	public int percent() {
		return placements.isEmpty() ? 100 : (int) ((long) placed() * 100L / placements.size());
	}

	/**
	 * Lays the next slice.
	 *
	 * @return how many blocks this call placed
	 */
	public int tick() {
		int end = Math.min(next + BLOCKS_PER_TICK, placements.size());
		int from = next;

		for (int index = from; index < end; index++) {
			placer.place(world, placements.get(index));
		}

		next = end;
		return end - from;
	}

	/**
	 * Runs the completion, once.
	 *
	 * <p>Separate from {@link #tick} so the caller can drop the job first and have whatever this
	 * does see a build that has finished rather than one still officially in progress. Anybody
	 * standing in the room is put on the new spawn from here rather than when the command ran,
	 * because when the command ran there was nothing under the new spawn to stand on.
	 */
	public void finish() {
		if (finished) {
			return;
		}

		finished = true;
		FreezeMute.LOGGER.info("Lobby: finished laying {} blocks", placements.size());

		if (whenDone != null) {
			whenDone.run();
		}
	}
}
