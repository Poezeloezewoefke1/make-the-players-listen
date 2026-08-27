package dev.mtpl.freezemute.lobby;

import java.util.UUID;

import com.google.gson.JsonObject;

/** A finished run: who did it, how long it took, and when. */
public record CourseRecord(UUID uuid, String name, long millis, long at) {
	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("uuid", uuid.toString());
		object.addProperty("name", name);
		object.addProperty("millis", millis);
		object.addProperty("at", at);
		return object;
	}

	public static CourseRecord fromJson(JsonObject object) {
		try {
			return new CourseRecord(
					UUID.fromString(object.get("uuid").getAsString()),
					object.get("name").getAsString(),
					object.get("millis").getAsLong(),
					object.has("at") ? object.get("at").getAsLong() : 0L);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	/** {@code 1:02.35} - the format everyone reads times in. */
	public static String format(long millis) {
		long total = Math.max(0L, millis);
		long minutes = total / 60_000L;
		long seconds = (total % 60_000L) / 1000L;
		long hundredths = (total % 1000L) / 10L;

		if (minutes > 0L) {
			return String.format("%d:%02d.%02d", minutes, seconds, hundredths);
		}

		return String.format("%d.%02d", seconds, hundredths);
	}
}
