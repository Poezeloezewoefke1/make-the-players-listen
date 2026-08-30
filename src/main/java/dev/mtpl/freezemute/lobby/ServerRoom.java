package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.command.Permissions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The real room: {@link Room} backed by a running server.
 *
 * <p>Deliberately thin. Everything here is a one line hand-off, because everything worth getting
 * wrong lives in {@link LobbyRules}, on the other side of the seam, where it can be tested.
 */
public final class ServerRoom implements Room {
	private final MinecraftServer server;

	public ServerRoom(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public List<Occupant> occupants() {
		List<Occupant> occupants = new ArrayList<>();

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			occupants.add(new ServerOccupant(server, player));
		}

		return occupants;
	}

	@Override
	public Occupant occupant(UUID uuid) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
		return player == null ? null : new ServerOccupant(server, player);
	}

	@Override
	public boolean built() {
		return LobbyDimension.world(server) != null && PlayerWorld.available();
	}

	@Override
	public void dropBarsExcept(Set<UUID> keep) {
		LobbyManager.dropBarsNotWaiting(keep);
	}

	@Override
	public void log(String message) {
		FreezeMute.LOGGER.info(message);
	}

	/** One real player. */
	private record ServerOccupant(MinecraftServer server, ServerPlayerEntity player) implements Occupant {
		@Override
		public UUID uuid() {
			return player.getUuid();
		}

		@Override
		public String name() {
			return player.getGameProfile().name();
		}

		@Override
		public boolean staff() {
			return Permissions.isStaff(player);
		}

		@Override
		public boolean inLobby() {
			return LobbyManager.isInLobby(player);
		}

		@Override
		public boolean member() {
			return LobbyManager.isMember(player);
		}

		@Override
		public void sendToLobby() {
			LobbyManager.sendToLobby(server, player);
		}

		@Override
		public void admit(boolean announce) {
			LobbyManager.admit(server, player, announce);
		}

		@Override
		public void becomeMember() {
			LobbyManager.becomeMember(server, player);
		}

		@Override
		public void letOut() {
			LobbyManager.sendToWorld(server, player,
					Text.literal("You are staff now, so the lobby has let you go.").formatted(Formatting.GREEN));
		}

		@Override
		public void showBar(int position, int total, boolean open) {
			LobbyManager.updateBar(player, position, total, open);
		}

		@Override
		public void message(String text) {
			player.sendMessage(Text.literal(text).formatted(Formatting.YELLOW));
		}
	}
}
