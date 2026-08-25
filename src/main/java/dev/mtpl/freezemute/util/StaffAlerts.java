package dev.mtpl.freezemute.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FreezeMuteConfig;
import dev.mtpl.freezemute.command.Permissions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Tells online staff when somebody is testing their punishment.
 *
 * <p>Throttled per player, because a frozen player holding W produces twenty packets a second
 * and nobody wants that in chat.
 */
public final class StaffAlerts {
	private static final Map<UUID, Long> LAST_ALERT = new ConcurrentHashMap<>();

	private StaffAlerts() {
	}

	/** A frozen player is pushing against the freeze. */
	public static void frozenPlayerIsTrying(ServerPlayerEntity player) {
		send(player, Text.literal(player.getGameProfile().name() + " is trying to move while frozen.")
				.formatted(Formatting.GRAY));
	}

	/** A muted player tried to say something. Staff see what it was. */
	public static void mutedPlayerTriedToTalk(ServerPlayerEntity player, String message) {
		String text = message == null || message.isBlank() ? "" : ": " + message;
		send(player, Text.literal(player.getGameProfile().name() + " tried to talk while muted" + text)
				.formatted(Formatting.GRAY));
	}

	private static void send(ServerPlayerEntity about, Text message) {
		FreezeMuteConfig config = FreezeMuteConfig.get();

		if (!config.notifyStaff) {
			return;
		}

		MinecraftServer server = FreezeMute.server();

		if (server == null) {
			return;
		}

		long now = System.currentTimeMillis();
		Long previous = LAST_ALERT.get(about.getUuid());

		if (previous != null && now - previous < config.staffNotifyCooldownMillis()) {
			return;
		}

		LAST_ALERT.put(about.getUuid(), now);

		for (ServerPlayerEntity staff : server.getPlayerManager().getPlayerList()) {
			if (Permissions.isStaff(staff)) {
				staff.sendMessage(message);
			}
		}
	}

	/** Forgets the throttle for a player, so the next attempt is reported straight away. */
	public static void forget(UUID uuid) {
		LAST_ALERT.remove(uuid);
	}
}
