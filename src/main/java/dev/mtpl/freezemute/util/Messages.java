package dev.mtpl.freezemute.util;

import dev.mtpl.freezemute.ModerationData.FreezeEntry;
import dev.mtpl.freezemute.ModerationData.MuteEntry;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** All player facing text lives here so the wording stays consistent. */
public final class Messages {
	private Messages() {
	}

	// ------------------------------------------------------------------ freeze

	public static MutableText youAreFrozen(FreezeEntry entry) {
		StringBuilder builder = new StringBuilder("You have been frozen by ")
				.append(entry.source())
				.append(' ')
				.append(describeRemaining(entry.until()));
		appendReason(builder, entry.reason());
		builder.append(" You cannot move until it is lifted.");
		return Text.literal(builder.toString()).formatted(Formatting.RED);
	}

	public static MutableText youAreStillFrozen(FreezeEntry entry) {
		StringBuilder builder = new StringBuilder("You are still frozen ").append(describeRemaining(entry.until()));
		appendReason(builder, entry.reason());
		return Text.literal(builder.toString()).formatted(Formatting.RED);
	}

	public static MutableText youAreUnfrozen() {
		return Text.literal("You are no longer frozen - you can move again.").formatted(Formatting.GREEN);
	}

	// -------------------------------------------------------------------- mute

	public static MutableText youAreMuted(MuteEntry entry) {
		StringBuilder builder = new StringBuilder("You are muted ").append(describeRemaining(entry.until()));
		appendReason(builder, entry.reason());
		return Text.literal(builder.toString()).formatted(Formatting.RED);
	}

	/**
	 * Told to somebody whose mute has been lifted.
	 *
	 * <p>Which is not the same as being able to talk. Chat is off in the lobby for everybody in it,
	 * so telling somebody waiting in line that they can chat again would have them typing into a
	 * room that still swallows it - and wondering why, having just been told otherwise.
	 */
	public static MutableText youAreUnmuted(boolean stillInTheLobby) {
		if (stillInTheLobby) {
			return Text.literal("You are no longer muted. Chat is still off while you are in the lobby, "
					+ "but staff can hear you.").formatted(Formatting.GREEN);
		}

		return Text.literal("You are no longer muted - you can chat again.").formatted(Formatting.GREEN);
	}

	// ------------------------------------------------------------------ shared

	/** "permanently", or "for 2h 30m" when there is an end in sight. */
	public static String describeRemaining(long until) {
		if (until <= 0L) {
			return "permanently";
		}

		return "for " + Durations.format(Math.max(0L, until - System.currentTimeMillis()));
	}

	private static void appendReason(StringBuilder builder, String reason) {
		if (reason != null && !reason.isBlank()) {
			builder.append(" - reason: ").append(reason);
		}

		builder.append('.');
	}

	public static MutableText success(String text) {
		return Text.literal(text).formatted(Formatting.YELLOW);
	}

	public static MutableText failure(String text) {
		return Text.literal(text).formatted(Formatting.RED);
	}

	public static MutableText header(String text) {
		return Text.literal(text).formatted(Formatting.GOLD);
	}

	public static MutableText listEntry(String text) {
		return Text.literal(text).formatted(Formatting.WHITE);
	}
}
