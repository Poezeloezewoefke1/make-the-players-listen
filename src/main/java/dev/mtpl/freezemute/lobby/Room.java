package dev.mtpl.freezemute.lobby;

import java.util.List;
import java.util.UUID;

/** The server, as the lobby's rules see it. */
public interface Room {
	List<Occupant> occupants();

	/** The occupant with this id, or null when they are not connected. */
	Occupant occupant(UUID uuid);

	/** False when the dimension is missing, in which case nobody can be sent anywhere. */
	boolean built();

	/** Takes the boss bar away from everybody whose id is not in the set. */
	void dropBarsExcept(java.util.Set<UUID> keep);

	void log(String message);
}
