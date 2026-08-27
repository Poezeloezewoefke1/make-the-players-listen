package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One parkour course.
 *
 * <p>A course is a start, any number of checkpoints and a finish. Touching the start begins a run,
 * every checkpoint has to be taken in order, and touching the finish while the last checkpoint has
 * been taken stops the clock. Checkpoints double as the place a fall puts you back on.
 */
public record Course(String name, Spot start, List<Spot> checkpoints, Spot finish) {
	public Course {
		checkpoints = List.copyOf(checkpoints);
	}

	public static Course starting(String name, Spot start) {
		return new Course(name, start, List.of(), null);
	}

	public boolean playable() {
		return finish != null;
	}

	public Course withCheckpoint(Spot spot) {
		List<Spot> updated = new ArrayList<>(checkpoints);
		updated.add(spot);
		return new Course(name, start, updated, finish);
	}

	public Course withoutLastCheckpoint() {
		if (checkpoints.isEmpty()) {
			return this;
		}

		return new Course(name, start, checkpoints.subList(0, checkpoints.size() - 1), finish);
	}

	public Course withStart(Spot spot) {
		return new Course(name, spot, checkpoints, finish);
	}

	public Course withFinish(Spot spot) {
		return new Course(name, start, checkpoints, spot);
	}

	/** Where a fall puts a runner back: the last checkpoint they reached, or the start. */
	public Spot respawnFor(int checkpointsTaken) {
		if (checkpointsTaken <= 0 || checkpoints.isEmpty()) {
			return start;
		}

		return checkpoints.get(Math.min(checkpointsTaken, checkpoints.size()) - 1);
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("name", name);
		object.add("start", start.toJson());

		JsonArray array = new JsonArray();

		for (Spot spot : checkpoints) {
			array.add(spot.toJson());
		}

		object.add("checkpoints", array);

		if (finish != null) {
			object.add("finish", finish.toJson());
		}

		return object;
	}

	public static Course fromJson(JsonObject object) {
		try {
			String name = object.get("name").getAsString();
			Spot start = Spot.fromJson(object.getAsJsonObject("start"), null);

			if (name.isBlank() || start == null) {
				return null;
			}

			List<Spot> checkpoints = new ArrayList<>();
			JsonArray array = object.getAsJsonArray("checkpoints");

			if (array != null) {
				for (JsonElement element : array) {
					Spot spot = element.isJsonObject() ? Spot.fromJson(element.getAsJsonObject(), null) : null;

					if (spot != null) {
						checkpoints.add(spot);
					}
				}
			}

			return new Course(name, start, checkpoints, Spot.fromJson(object.getAsJsonObject("finish"), null));
		} catch (RuntimeException exception) {
			return null;
		}
	}
}
