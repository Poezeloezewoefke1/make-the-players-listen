package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FreezeMuteConfig;
import dev.mtpl.freezemute.command.Permissions;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.World;

/**
 * The lobby itself: who is in it, who gets let out, and what they may do while they wait.
 *
 * <p>Members are held by the rules the freeze already enforces, with movement left switched on -
 * {@code ServerPlayNetworkHandlerMixin} asks this class the same way it asks about a freeze, so
 * breaking, placing, hitting, dropping and inventory clicks are refused through code that was
 * already written and tested. Nothing about walking, jumping or parkour is touched.
 *
 * <p>Staff are never members. They are routed nowhere, hidden from nobody, and keep chat, voice
 * and every interaction.
 */
public final class LobbyManager {
	/** The team exists to switch collisions and floating names off, nothing else. */
	private static final String TEAM = "astra_lobby";

	private static final Set<UUID> MEMBERS = ConcurrentHashMap.newKeySet();
	/** Entity id to player id, for the packet filter that does the hiding. */
	private static final Map<Integer, UUID> MEMBER_ENTITY_IDS = new ConcurrentHashMap<>();
	private static final Map<UUID, ServerBossBar> BARS = new ConcurrentHashMap<>();
	/** Set while at least one member is in the lobby, so the packet filter can leave early. */
	private static volatile boolean anyMembers;

	private LobbyManager() {
	}

	// ------------------------------------------------------------------ asking

	/** True for a player the lobby holds: in the lobby, and not staff. */
	public static boolean isMember(ServerPlayerEntity player) {
		return player != null && MEMBERS.contains(player.getUuid());
	}

	public static boolean isMember(UUID uuid) {
		return uuid != null && MEMBERS.contains(uuid);
	}

	public static int memberCount() {
		return MEMBERS.size();
	}

	/** True when this entity id belongs to a member who should not be rendered by other members. */
	public static boolean isHiddenEntity(int entityId) {
		return anyMembers && MEMBER_ENTITY_IDS.containsKey(entityId);
	}

	/** Cheap enough to sit in front of every outgoing packet. */
	public static boolean isolating() {
		return anyMembers && FreezeMuteConfig.get().lobbyIsolateMembers;
	}

	// ------------------------------------------------------------------ routing

	/**
	 * Decides where a player belongs the moment they finish connecting.
	 *
	 * <p>Everyone who is not staff and has not been let in goes to the lobby and takes a place in
	 * line. Somebody who already holds a slot walks back into the world exactly where they left it,
	 * which is what the grace window is for.
	 */
	public static void onJoin(MinecraftServer server, ServerPlayerEntity player) {
		LobbyState state = LobbyState.get();

		if (!state.enabled()) {
			return;
		}

		UUID uuid = player.getUuid();
		String name = player.getGameProfile().name();
		long now = System.currentTimeMillis();
		boolean inLobby = isInLobby(player);

		if (Permissions.isStaff(player)) {
			// Staff are not queued and do not take up a slot - the cap is there to keep the world
			// quiet enough to film in, and the people filming it are not the problem. They are not
			// moved either: somebody who logged out while building the parkour logs back into it.
			state.dequeue(uuid);
			return;
		}

		if (Permissions.hasEarlyAccess(player) || state.hasEarlyAccess(uuid)) {
			state.dequeue(uuid);
			state.admit(uuid, name, now);

			if (inLobby) {
				sendToWorld(server, player, Text.literal("Early access - straight in.").formatted(Formatting.GREEN));
			}

			return;
		}

		if (state.isAdmitted(uuid)) {
			state.admit(uuid, name, now);

			if (inLobby) {
				// They were sent back to the lobby by a restart but never lost their slot.
				sendToWorld(server, player, Text.literal("Your slot was still held for you.").formatted(Formatting.GREEN));
			}

			return;
		}

		LobbyState.Waiting waiting = state.enqueue(uuid, name, now);
		sendToLobby(server, player);
		player.sendMessage(Text.literal("You are in the queue at place " + state.position(uuid)
				+ " of " + state.queueSize() + ". You will be let in automatically.").formatted(Formatting.YELLOW));

		if (!waiting.online()) {
			FreezeMute.LOGGER.warn("Lobby: {} rejoined but their queue entry still says offline", name);
		}
	}

	/** Moves a player into the lobby and applies the member rules. */
	public static void sendToLobby(MinecraftServer server, ServerPlayerEntity player) {
		ServerWorld lobby = LobbyDimension.world(server);
		LobbyState state = LobbyState.get();

		if (lobby == null) {
			player.sendMessage(Text.literal("The lobby is not built yet - staff have been told.")
					.formatted(Formatting.RED));
			return;
		}

		LobbyDimension.ensurePlatform(lobby, state.spawn());

		if (!isInLobby(player)) {
			rememberWhereTheyWere(player);
		}

		Spot spawn = state.spawn();
		player.teleport(lobby, spawn.x(), spawn.y(), spawn.z(), Set.<PositionFlag>of(), spawn.yaw(), spawn.pitch(), true);
		becomeMember(server, player);
	}

	/**
	 * Applies everything that makes somebody a lobby member. Safe to call twice.
	 *
	 * <p>Staff are never members, even when they are standing in the lobby. {@code /lobby} is how
	 * they go and look at the room, and a moderator who arrives in adventure mode unable to touch
	 * anything is not much use to the people waiting in it.
	 */
	public static void becomeMember(MinecraftServer server, ServerPlayerEntity player) {
		if (Permissions.isStaff(player)) {
			return;
		}

		player.changeGameMode(GameMode.ADVENTURE);
		// Nobody should die waiting in line, and a parkour fall is caught before the void anyway.
		player.setInvulnerable(true);

		MEMBERS.add(player.getUuid());
		MEMBER_ENTITY_IDS.put(player.getId(), player.getUuid());
		anyMembers = true;

		joinTeam(server, player);
		hideFromOtherMembers(server, player);
	}

	/** Lets a player through: back to the world they came from, in survival, out of the queue. */
	public static void admit(MinecraftServer server, ServerPlayerEntity player, boolean announce) {
		LobbyState state = LobbyState.get();
		UUID uuid = player.getUuid();

		state.dequeue(uuid);
		state.admit(uuid, player.getGameProfile().name(), System.currentTimeMillis());
		sendToWorld(server, player, null);

		if (announce) {
			// The title fires here, when a slot really opened - not when somebody reaches the
			// front of the line, because being first still means waiting.
			title(player, Text.literal("You're in").formatted(Formatting.GREEN),
					Text.literal("Have fun").formatted(Formatting.WHITE));
			playCue(player);
			player.sendMessage(Text.literal("A slot opened up - welcome in.").formatted(Formatting.GREEN));
		}
	}

	/**
	 * Puts a player back into the world, undoing every member rule.
	 *
	 * <p>A player who is not actually in the lobby is only released, never moved. Otherwise
	 * {@code /queue bypass} on somebody already playing would drag them to spawn, which is the
	 * opposite of a favour.
	 */
	public static void sendToWorld(MinecraftServer server, ServerPlayerEntity player, Text message) {
		boolean wasInLobby = isInLobby(player);
		LobbyState.Return remembered = wasInLobby ? LobbyState.get().takeReturn(player.getUuid()) : null;

		stopBeingMember(server, player);

		if (!wasInLobby) {
			if (message != null) {
				player.sendMessage(message);
			}

			return;
		}

		ServerWorld destination = null;
		Spot target = null;
		GameMode mode = GameMode.SURVIVAL;

		if (remembered != null) {
			Identifier id = Identifier.tryParse(remembered.dimension());

			if (id != null) {
				destination = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
				target = remembered.spot();
			}

			mode = GameMode.byId(remembered.gameMode(), GameMode.SURVIVAL);
		}

		if (destination == null) {
			// Nothing remembered, so fall back on where the server itself says players start.
			WorldProperties.SpawnPoint spawn = server.getSpawnPoint();
			destination = server.getWorld(spawn.getDimension());

			if (destination == null) {
				destination = server.getOverworld();
			}

			BlockPos pos = spawn.getPos();
			target = new Spot(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYaw(), player.getPitch());
		}

		if (destination != null && target != null) {
			player.teleport(destination, target.x(), target.y(), target.z(), Set.<PositionFlag>of(),
					target.yaw(), target.pitch(), true);
		}

		player.changeGameMode(mode);
		player.setInvulnerable(false);

		if (message != null) {
			player.sendMessage(message);
		}
	}

	/** Drops the member rules without moving anybody. */
	public static void stopBeingMember(MinecraftServer server, ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		MEMBERS.remove(uuid);
		MEMBER_ENTITY_IDS.remove(player.getId());
		anyMembers = !MEMBERS.isEmpty();

		leaveTeam(server, player);
		removeBar(player);
	}

	// ----------------------------------------------------------------- leaving

	/** Called when a player disconnects: their entry goes offline and the grace clock starts. */
	public static void onLeave(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		long now = System.currentTimeMillis();
		LobbyState state = LobbyState.get();

		state.markWaitingOffline(uuid, now);
		state.markAdmittedOffline(uuid, now);

		MEMBERS.remove(uuid);
		MEMBER_ENTITY_IDS.remove(player.getId());
		anyMembers = !MEMBERS.isEmpty();
		removeBar(player);
	}

	/**
	 * Called when staff kick somebody. A kick has to void the grace, otherwise kicking an idle
	 * player leaves their slot held for another five minutes and the kick achieves nothing.
	 */
	public static void onKicked(UUID uuid, String name) {
		LobbyState state = LobbyState.get();
		boolean waiting = state.dequeue(uuid) != null;
		boolean held = state.release(uuid) != null;

		if (waiting || held) {
			FreezeMute.LOGGER.info("Lobby: {} was kicked, so their {} is gone straight away",
					name, held ? "slot" : "place in line");
		}
	}

	// ------------------------------------------------------------- the boss bar

	/**
	 * Position and total live on a boss bar, where they can change every second without pushing
	 * anything out of chat.
	 */
	public static void updateBar(ServerPlayerEntity player, int position, int total, boolean open) {
		ServerBossBar bar = BARS.computeIfAbsent(player.getUuid(), key -> {
			ServerBossBar created = new ServerBossBar(Text.literal("Queue"), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
			created.addPlayer(player);
			return created;
		});

		String text = open
				? "Queue - place " + position + " of " + total
				: "Queue closed - place " + position + " of " + total;
		bar.setName(Text.literal(text));
		bar.setColor(open ? BossBar.Color.BLUE : BossBar.Color.RED);
		// Full at the front of the line, empty at the back, so the bar drains as you wait.
		bar.setPercent(total <= 1 ? 1.0F : (float) (total - position) / (float) (total - 1));
	}

	public static void removeBar(ServerPlayerEntity player) {
		ServerBossBar bar = BARS.remove(player.getUuid());

		if (bar != null) {
			bar.clearPlayers();
		}
	}

	// ------------------------------------------------------------- the trimmings

	public static void title(ServerPlayerEntity player, Text title, Text subtitle) {
		ServerPlayNetworkHandler handler = player.networkHandler;

		if (handler == null) {
			return;
		}

		handler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
		handler.sendPacket(new SubtitleS2CPacket(subtitle));
		handler.sendPacket(new TitleS2CPacket(title));
	}

	/**
	 * The sound that goes with the title.
	 *
	 * <p>Sent as a packet holding the sound by name rather than looked up in the registry: the
	 * packet format allows it, every vanilla client understands it, and it keeps the mod off one
	 * more piece of the API that gets renamed between versions.
	 */
	public static void playCue(ServerPlayerEntity player) {
		ServerPlayNetworkHandler handler = player.networkHandler;

		if (handler == null) {
			return;
		}

		RegistryEntry<SoundEvent> sound =
				RegistryEntry.of(SoundEvent.of(Identifier.of("minecraft", "entity.player.levelup")));
		handler.sendPacket(new PlaySoundS2CPacket(sound, SoundCategory.MASTER,
				player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F, player.getUuid().getLeastSignificantBits()));
	}

	public static void actionBar(ServerPlayerEntity player, Text text) {
		player.sendMessage(text, true);
	}

	// ------------------------------------------------------------------- where

	public static boolean isInLobby(ServerPlayerEntity player) {
		ServerWorld world = PlayerWorld.of(player);
		return world != null && LobbyDimension.KEY.equals(world.getRegistryKey());
	}

	private static void rememberWhereTheyWere(ServerPlayerEntity player) {
		ServerWorld world = PlayerWorld.of(player);

		if (world == null) {
			return;
		}

		RegistryKey<World> key = world.getRegistryKey();

		if (LobbyDimension.KEY.equals(key)) {
			return;
		}

		LobbyState.get().rememberReturn(player.getUuid(), key.getValue().toString(), Spot.of(player),
				player.interactionManager.getGameMode().getId());
	}

	// -------------------------------------------------------------------- team

	/**
	 * Members share a team so they cannot shove each other off a jump and so no name tag floats
	 * over an otherwise invisible player. A player who already belongs to a team is left alone -
	 * a lobby is not worth losing somebody's rank colour over.
	 */
	private static void joinTeam(MinecraftServer server, ServerPlayerEntity player) {
		Scoreboard scoreboard = server.getScoreboard();
		Team team = ensureTeam(scoreboard);
		String name = player.getGameProfile().name();
		Team current = scoreboard.getScoreHolderTeam(name);

		if (current == null) {
			scoreboard.addScoreHolderToTeam(name, team);
		}
	}

	private static void leaveTeam(MinecraftServer server, ServerPlayerEntity player) {
		Scoreboard scoreboard = server.getScoreboard();
		String name = player.getGameProfile().name();
		Team current = scoreboard.getScoreHolderTeam(name);

		if (current != null && TEAM.equals(current.getName())) {
			scoreboard.removeScoreHolderFromTeam(name, current);
		}
	}

	private static Team ensureTeam(Scoreboard scoreboard) {
		Team team = scoreboard.getTeam(TEAM);

		if (team == null) {
			team = scoreboard.addTeam(TEAM);
			team.setDisplayName(Text.literal("Lobby"));
		}

		team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
		team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
		team.setFriendlyFireAllowed(false);
		team.setShowFriendlyInvisibles(false);
		return team;
	}

	// --------------------------------------------------------------- isolation

	/**
	 * Makes a player who just became a member disappear from the other members, and makes the
	 * other members disappear from them.
	 *
	 * <p>The hiding itself is done by dropping spawn packets on the way out
	 * ({@code ServerCommonNetworkHandlerMixin}); this only has to make the client forget whoever it
	 * was already shown, which a quick round trip out of tracking range does for free. Rather than
	 * fight entity tracking, the pair is simply re-sent: the teleport into the lobby has already
	 * cleared everyone's view, so all that is left is to keep it that way.
	 */
	private static void hideFromOtherMembers(MinecraftServer server, ServerPlayerEntity player) {
		if (!FreezeMuteConfig.get().lobbyIsolateMembers) {
			return;
		}

		Set<ServerPlayerEntity> others = new HashSet<>();

		for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
			if (other != player && isMember(other)) {
				others.add(other);
			}
		}

		for (ServerPlayerEntity other : others) {
			forget(player, other);
			forget(other, player);
		}
	}

	/**
	 * Tells one client to drop another player it may already have been shown.
	 *
	 * <p>Kept deliberately small: the entity is removed client side, and the spawn packet that
	 * would bring it back is refused on the way out for as long as both are members.
	 */
	private static void forget(ServerPlayerEntity viewer, ServerPlayerEntity subject) {
		ServerPlayNetworkHandler handler = viewer.networkHandler;

		if (handler != null) {
			handler.sendPacket(new EntitiesDestroyS2CPacket(subject.getId()));
		}
	}

	/**
	 * Pulls everybody who is not staff back into the lobby - the panic button.
	 *
	 * <p>The line is rebuilt in the order people were let in, so whoever has been playing longest
	 * comes back out first when the session resumes. Slots are handed back, because a slot held by
	 * somebody standing in the lobby is a slot nobody can use.
	 *
	 * @return how many players were moved
	 */
	public static int recallEveryone(MinecraftServer server) {
		LobbyState state = LobbyState.get();
		List<ServerPlayerEntity> moving = new ArrayList<>();

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isInLobby(player) && !Permissions.isStaff(player)) {
				moving.add(player);
			}
		}

		moving.sort(Comparator.comparingLong(player -> {
			LobbyState.Admitted entry = state.admittedEntry(player.getUuid());
			return entry == null ? Long.MAX_VALUE : entry.since();
		}));

		long now = System.currentTimeMillis();

		for (ServerPlayerEntity player : moving) {
			state.release(player.getUuid());
			sendToLobby(server, player);
			state.enqueue(player.getUuid(), player.getGameProfile().name(), now);
			player.sendMessage(Text.literal("You have been sent back to the lobby.").formatted(Formatting.YELLOW));
		}

		return moving.size();
	}

	/** Wipes every runtime trace of the lobby, for a server that is shutting the session down. */
	public static void forgetEveryone() {
		MEMBERS.clear();
		MEMBER_ENTITY_IDS.clear();
		anyMembers = false;

		for (ServerBossBar bar : BARS.values()) {
			bar.clearPlayers();
		}

		BARS.clear();
	}
}
