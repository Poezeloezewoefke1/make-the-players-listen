package dev.mtpl.freezemute.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.mtpl.freezemute.lobby.LobbyBuilder.Step;

import org.junit.jupiter.api.Test;

/**
 * Whether the generated parkour can actually be run.
 *
 * <p>A course that looks fine and cannot be jumped is worse than no course, and it is not
 * something anybody would notice from a compiling build - so the shape is checked here rather
 * than discovered by whoever tries it first.
 */
class CourseShapeTest {
	/** A sprint jump clears four blocks of gap on the flat; three is the comfortable figure. */
	private static final double MAX_GAP = 3.6D;

	private static List<Step> steps() {
		return LobbyBuilder.courseSteps(0, 0, 74);
	}

	@Test
	void everyJumpIsWithinReach() {
		List<Step> steps = steps();

		for (int index = 1; index < steps.size(); index++) {
			Step from = steps.get(index - 1);
			Step to = steps.get(index);

			double dx = to.x() - from.x();
			double dz = to.z() - from.z();
			double gap = Math.sqrt(dx * dx + dz * dz);

			assertTrue(gap <= MAX_GAP,
					"jump " + index + " is " + String.format("%.2f", gap) + " blocks, which is too far");
			assertTrue(gap >= 1.0D, "jump " + index + " lands on the block it started from");
		}
	}

	@Test
	void nothingRisesMoreThanOneBlockAtATime() {
		List<Step> steps = steps();

		for (int index = 1; index < steps.size(); index++) {
			int rise = steps.get(index).y() - steps.get(index - 1).y();
			assertTrue(rise >= 0 && rise <= 1, "jump " + index + " changes height by " + rise);
		}
	}

	@Test
	void aRiseIsNeverAskedForOnTheLongestJumps() {
		List<Step> steps = steps();

		for (int index = 1; index < steps.size(); index++) {
			Step from = steps.get(index - 1);
			Step to = steps.get(index);

			if (to.y() <= from.y()) {
				continue;
			}

			double dx = to.x() - from.x();
			double dz = to.z() - from.z();
			double gap = Math.sqrt(dx * dx + dz * dz);

			// Going up costs reach, so an uphill jump has to be shorter than a flat one.
			assertTrue(gap <= 3.2D,
					"jump " + index + " asks for " + String.format("%.2f", gap) + " blocks and a block up");
		}
	}

	@Test
	void itStartsOnGoldAndEndsOnDiamondWithCheckpointsBetween() {
		List<Step> steps = steps();

		assertTrue(steps.get(0).start());
		assertTrue(steps.get(steps.size() - 1).finish());
		assertEquals(2, steps.stream().filter(Step::checkpoint).count());
		assertEquals(4, steps.stream().filter(Step::pad).count());
	}

	@Test
	void theCourseClimbsSomewhereWorthClimbing() {
		List<Step> steps = steps();
		int climb = steps.get(steps.size() - 1).y() - steps.get(0).y();

		assertTrue(climb >= 6, "a course that only rises " + climb + " blocks is a walk");
	}
}
