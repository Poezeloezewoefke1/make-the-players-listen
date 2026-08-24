package dev.mtpl.freezemute.util;

import dev.mtpl.freezemute.ModerationData.MuteEntry;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** All player facing text lives here so the wording stays consistent. */
public final class Messages {
	private Messages() {
	}

	public static MutableText youAreFrozen(String source) {
		return Text.literal("You have been frozen by " + source + ". You cannot move until an operator unfreezes you.")
				.formatted(Formatting.RED);
	}

	public static MutableText youAreStillFrozen() {
		return Text.literal("You are still frozen. Ask an operator to unfreeze you.").formatted(Formatting.RED);
	}

	public static MutableText youAreUnfrozen() {
		return Text.literal("You are no longer frozen - you can move again.").formatted(Formatting.GREEN);
	}

	public static MutableText youAreMuted(MuteEntry mute) {
		StringBuilder builder = new StringBuilder("You are muted");

		if (mute.permanent()) {
			builder.append(" permanently");
		} else {
			builder.append(" for another ").append(Durations.format(mute.remainingMillis(System.currentTimeMillis())));
		}

		if (!mute.reason().isBlank()) {
			builder.append(" - reason: ").append(mute.reason());
		}

		builder.append('.');
		return Text.literal(builder.toString()).formatted(Formatting.RED);
	}

	public static MutableText youAreUnmuted() {
		return Text.literal("You are no longer muted - you can chat again.").formatted(Formatting.GREEN);
	}

	/** "permanently" or "for 2h 30m", used in operator feedback. */
	public static String describeDuration(MuteEntry mute) {
		return mute.permanent()
				? "permanently"
				: "for " + Durations.format(mute.remainingMillis(System.currentTimeMillis()));
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
