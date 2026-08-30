package dev.mtpl.freezemute.lobby;

import com.google.gson.JsonObject;

import net.minecraft.server.network.ServerPlayerEntity;

/** A place to stand: a position plus the direction you are facing. */
public record Spot(double x, double y, double z, float yaw, float pitch) {
	public static Spot of(ServerPlayerEntity player) {
		return new Spot(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
	}

	/** Squared distance to a position, so the hot path never calls {@code sqrt}. */
	public double distanceSquared(double otherX, double otherY, double otherZ) {
		double dx = x - otherX;
		double dy = y - otherY;
		double dz = z - otherZ;
		return dx * dx + dy * dy + dz * dz;
	}

	/**
	 * Coordinates as a person reads them.
	 *
	 * <p>Formatted against the root locale rather than the server's. A host set to German turns
	 * {@code %.1f} into {@code 65,0}, and a coordinate somebody is meant to type back into
	 * {@code /tp} is not the place to be following local number conventions.
	 */
	public String describe() {
		return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("x", x);
		object.addProperty("y", y);
		object.addProperty("z", z);
		object.addProperty("yaw", yaw);
		object.addProperty("pitch", pitch);
		return object;
	}

	public static Spot fromJson(JsonObject object, Spot fallback) {
		if (object == null) {
			return fallback;
		}

		try {
			return new Spot(
					object.get("x").getAsDouble(),
					object.get("y").getAsDouble(),
					object.get("z").getAsDouble(),
					object.has("yaw") ? object.get("yaw").getAsFloat() : 0.0F,
					object.has("pitch") ? object.get("pitch").getAsFloat() : 0.0F);
		} catch (RuntimeException exception) {
			return fallback;
		}
	}
}
