package dev.mtpl.freezemute.lobby;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
	/** Players who have just joined, counting down the ticks until it is safe to move them. */
	private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
	/** Long enough for the join to finish, short enough that nobody notices the delay. */
	private static final int JOIN_DELAY_TICKS = 10;
	/** Members who arrived recently, and how many ticks their hiding is still being re-stated. */
	private static final Map<UUID, Integer> SETTLING = new ConcurrentHashMap<>();
	/** Three seconds is far longer than entity tracking needs, and costs nothing after that. */
	private static final int SETTLE_TICKS = 60;
	private static final int SETTLE_EVERY = 5;
	private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
	private static final long CLICK_COOLDOWN_MILLIS = 500L;

	private static int settleTick;
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
		if (!LobbyState.get().enabled()) {
			return;
		}

		// Not yet. A player is still being handed their chunks and their player list at this
		// point, and moving them to another dimension in the middle of that leaves the client
		// stuck on the loading screen. The routing is done from the tick loop a moment later,
		// once the join has finished the way vanilla expects it to.
		PENDING.put(player.getUuid(), JOIN_DELAY_TICKS);
	}

	/**
	 * Routes the players whose join has settled. Called once per tick.
	 */
	public static void tickPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, Integer> entry : PENDING.entrySet()) {
			int remaining = entry.getValue() - 1;

			if (remaining > 0) {
				PENDING.put(entry.getKey(), remaining);
				continue;
			}

			PENDING.remove(entry.getKey());
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

			if (player != null) {
				route(server, player);
			}
		}
	}

	private static void route(MinecraftServer server, ServerPlayerEntity player) {
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
			// And they hand back a slot they were holding from before they were staff, which
			// nobody else could use while it sat there.
			state.release(uuid);
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

		if (state.joinedAtAPoint()) {
			// There is somewhere to ask, so nobody is put in the line for simply turning up.
			sendToLobby(server, player);
			player.sendMessage(Text.literal("Welcome. Right click the pedestal to join the queue - "
					+ "until then you are free to wander and try the parkour.").formatted(Formatting.YELLOW));
			return;
		}

		if (state.queueOpen() && state.queueSize() == 0 && hasFreeSlot(state)) {
			// Nobody is ahead of them and there is room right now, so there is nothing to wait
			// for. Showing them a queue they would leave a second later is worse than not
			// showing them one at all.
			state.admit(uuid, name, now);

			if (inLobby) {
				sendToWorld(server, player, null);
			}

			return;
		}

		state.enqueue(uuid, name, now);
		sendToLobby(server, player);
		player.sendMessage(Text.literal("You are in the queue at place " + state.position(uuid)
				+ " of " + state.queueSize() + ". You will be let in automatically.").formatted(Formatting.YELLOW));
	}

	/**
	 * A member right clicked while standing at the queue point, so they are asking for a place.
	 *
	 * <p>The click is judged by where the player is standing rather than by what they hit. That
	 * way it works against a block, an armour stand, or whatever NPC somebody puts on the pedestal
	 * later, without this having to understand any of them.
	 */
	public static void clickedQueuePoint(MinecraftServer server, ServerPlayerEntity player) {
		LobbyState state = LobbyState.get();
		UUID uuid = player.getUuid();

		long now = System.currentTimeMillis();
		Long last = LAST_CLICK.get(uuid);

		if (last != null && now - last < CLICK_COOLDOWN_MILLIS) {
			// One right click can arrive as two packets; only answer the first.
			return;
		}

		LAST_CLICK.put(uuid, now);

		if (state.isAdmitted(uuid)) {
			return;
		}

		if (state.waiting(uuid) != null) {
			player.sendMessage(Text.literal("You are already in the queue, at place "
					+ state.position(uuid) + " of " + state.queueSize() + ".").formatted(Formatting.YELLOW));
			return;
		}

		state.enqueue(uuid, player.getGameProfile().name(), now);
		playCue(player);
		player.sendMessage(Text.literal("You are in the queue at place " + state.position(uuid)
				+ " of " + state.queueSize() + ". You will be let in automatically - have a go at the "
				+ "parkour while you wait.").formatted(Formatting.GREEN));
	}

	/** True when the player is close enough to the queue point for a right click to count. */
	public static boolean atQueuePoint(ServerPlayerEntity player) {
		Spot point = LobbyState.get().queuePoint();

		if (point == null || player == null) {
			return false;
		}

		double radius = Math.max(1.0D, FreezeMuteConfig.get().lobbyQueuePointRadius);
		return point.distanceSquared(player.getX(), player.getY(), player.getZ()) <= radius * radius;
	}

	/** True when the cap would let one more player through right now. */
	public static boolean hasFreeSlot(LobbyState state) {
		return state.cap() <= 0 || state.slotsUsed() < state.cap();
	}

	/**
	 * Puts back anybody who is flagged as a member but is not in the lobby any more.
	 *
	 * <p>A queued player has more ways out of that room than the interaction blocks cover:
	 * {@code /kill} goes through invulnerability and respawns them in the world, dying at all
	 * makes a whole new entity, and any teleport command another mod provides - {@code /home},
	 * {@code /tpa}, {@code /spawn} - simply moves them. Rather than trying to name every one of
	 * those, the lobby checks where its members actually are and walks them back.
	 */
	public static void returnEscapees(MinecraftServer server) {
		if (MEMBERS.isEmpty() || !PlayerWorld.available() || LobbyDimension.world(server) == null) {
			// Without the world lookup every member looks like an escapee, and walking them back
			// once a second forever would be far worse than the hole it plugs. With no lobby to
			// walk them back to, sendToLobby would only apologise to them, once a second.
			return;
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (isMember(player) && !isInLobby(player)) {
				FreezeMute.LOGGER.info("Lobby: {} left the lobby without being let in, putting them back",
						player.getGameProfile().name());
				sendToLobby(server, player);
			}
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
			// Before becomeMember, which puts them in adventure mode - otherwise adventure is
			// what gets remembered and what they are handed back on the way out.
			rememberWhereTheyWere(player);
		}

		// Before the teleport, not after. Entity tracking introduces the new arrival to the room
		// on the following tick, and the filter that hides them can only do its job if they are
		// already flagged by then. Flagging afterwards leaves a one tick window in which the
		// packet naming them goes out unfiltered - which is exactly how one player ends up
		// visible to another while the other stays hidden.
		becomeMember(server, player);

		Spot spawn = state.spawn();
		player.teleport(lobby, spawn.x(), spawn.y(), spawn.z(), Set.<PositionFlag>of(), spawn.yaw(), spawn.pitch(), true);
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

		UUID uuid = player.getUuid();
		MEMBERS.add(uuid);
		// Dying makes a whole new entity with a new id, so drop whatever id they had before -
		// a stale one leaves the hiding filter looking for somebody who no longer exists.
		MEMBER_ENTITY_IDS.values().removeIf(uuid::equals);
		MEMBER_ENTITY_IDS.put(player.getId(), uuid);
		anyMembers = true;

		joinTeam(server, player);
		hideFromOtherMembers(player);
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
		MEMBER_ENTITY_IDS.values().removeIf(uuid::equals);
		anyMembers = !MEMBERS.isEmpty();

		leaveTeam(server, player);
		Parkour.forget(uuid);
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
		MEMBER_ENTITY_IDS.values().removeIf(uuid::equals);
		anyMembers = !MEMBERS.isEmpty();
		PENDING.remove(uuid);
		SETTLING.remove(uuid);
		LAST_CLICK.remove(uuid);
		// A half finished run is not worth keeping. Resuming one after a relog would count the
		// time somebody spent offline as part of their time on the course.
		Parkour.forget(uuid);
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
		ServerBossBar bar = BARS.computeIfAbsent(player.getUuid(), key ->
				new ServerBossBar(Text.literal("Queue"), BossBar.Color.BLUE, BossBar.Style.PROGRESS));

		// Dying builds a whole new player entity, and the bar is still pointed at the old one, so
		// it quietly stops being drawn. Re-attaching when the instance has changed fixes that;
		// checking first means the usual case sends no packets at all.
		if (!bar.getPlayers().contains(player)) {
			bar.clearPlayers();
			bar.addPlayer(player);
		}

		String text = open
				? "Queue - place " + position + " of " + total
				: "Queue closed - place " + position + " of " + total;
		bar.setName(Text.literal(text));
		bar.setColor(open ? BossBar.Color.BLUE : BossBar.Color.RED);
		// Full at the front of the line, empty at the back, so the bar drains as you wait.
		bar.setPercent(total <= 1 ? 1.0F : (float) (total - position) / (float) (total - 1));
	}

	public static void removeBar(ServerPlayerEntity player) {
		removeBar(player.getUuid());
	}

	public static void removeBar(UUID uuid) {
		ServerBossBar bar = BARS.remove(uuid);

		if (bar != null) {
			bar.clearPlayers();
		}
	}

	/**
	 * Drops the bars of anybody who is no longer waiting.
	 *
	 * <p>A bar is only ever refreshed for somebody in the queue, so one belonging to a player who
	 * left the queue but stayed in the room - which is what {@code /queue end} does to everybody -
	 * would otherwise sit on their screen showing a place they no longer hold.
	 */
	public static void dropBarsNotWaiting(Set<UUID> waiting) {
		if (BARS.isEmpty()) {
			return;
		}

		for (UUID uuid : Set.copyOf(BARS.keySet())) {
			if (!waiting.contains(uuid)) {
				removeBar(uuid);
			}
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
	 * Puts a new member on the list of people whose hiding is still being settled.
	 *
	 * <p>Doing the hiding once, right here, would achieve nothing: at this moment the arriving
	 * player has not been introduced to anybody, so there is nothing to take back, and the
	 * introduction arrives a tick later. {@link #reassertHiding} is what actually catches them.
	 */
	private static void hideFromOtherMembers(ServerPlayerEntity player) {
		SETTLING.put(player.getUuid(), SETTLE_TICKS);
	}

	/**
	 * Tells the clients of everybody involved to drop each other, repeatedly, for the first few
	 * seconds after somebody joins the room.
	 *
	 * <p>Refusing the packet that introduces two members is the main mechanism, but it only works
	 * if both of them are flagged when it goes out, and there are ways to become a member after
	 * the introduction has already happened - being made an operator and then not, a teleport in
	 * from outside, a respawn. Rather than reason about each of those, the pairing is simply
	 * re-stated until it is certainly true. Once nobody is settling this does nothing at all.
	 */
	private static void reassertHiding(MinecraftServer server) {
		if (SETTLING.isEmpty()) {
			return;
		}

		if (!FreezeMuteConfig.get().lobbyIsolateMembers) {
			SETTLING.clear();
			return;
		}

		List<ServerPlayerEntity> members = new ArrayList<>();

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (isMember(player)) {
				members.add(player);
			}
		}

		for (UUID uuid : Set.copyOf(SETTLING.keySet())) {
			Integer remaining = SETTLING.get(uuid);
			ServerPlayerEntity settling = server.getPlayerManager().getPlayer(uuid);

			if (remaining == null || remaining <= 0 || settling == null || !isMember(settling)) {
				SETTLING.remove(uuid);
				continue;
			}

			SETTLING.put(uuid, remaining - SETTLE_EVERY);

			int[] ids = members.stream()
					.filter(other -> other != settling)
					.mapToInt(ServerPlayerEntity::getId)
					.toArray();

			if (ids.length == 0) {
				continue;
			}

			// One packet tells the newcomer to forget the whole room.
			forget(settling, ids);

			// And one each tells the room to forget the newcomer.
			for (ServerPlayerEntity other : members) {
				if (other != settling) {
					forget(other, settling.getId());
				}
			}
		}
	}

	/**
	 * Keeps the entity id list honest.
	 *
	 * <p>The filter runs on a network thread and cannot go looking through the player list, so it
	 * reads a snapshot of which entity ids belong to members. Rebuilding that snapshot from the
	 * players who are actually here means it can never drift out of step with who is a member -
	 * which it otherwise does the moment somebody respawns and comes back as a new entity.
	 */
	private static void refreshEntityIds(MinecraftServer server) {
		if (MEMBERS.isEmpty()) {
			MEMBER_ENTITY_IDS.clear();
			return;
		}

		Map<Integer, UUID> fresh = new HashMap<>();

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (isMember(player)) {
				fresh.put(player.getId(), player.getUuid());
			}
		}

		// Add before removing, so the filter never reads an empty map mid-refresh.
		MEMBER_ENTITY_IDS.putAll(fresh);
		MEMBER_ENTITY_IDS.keySet().retainAll(fresh.keySet());
	}

	/** Per-tick upkeep for the room: who is here, who can see whom. */
	public static void tickMembers(MinecraftServer server) {
		refreshEntityIds(server);

		if (settleTick++ % SETTLE_EVERY == 0) {
			reassertHiding(server);
		}
	}

	/**
	 * Tells one client to drop another player it may already have been shown.
	 *
	 * <p>Kept deliberately small: the entity is removed client side, and the spawn packet that
	 * would bring it back is refused on the way out for as long as both are members.
	 */
	private static void forget(ServerPlayerEntity viewer, int... entityIds) {
		ServerPlayNetworkHandler handler = viewer.networkHandler;

		if (handler != null && entityIds.length > 0) {
			handler.sendPacket(new EntitiesDestroyS2CPacket(entityIds));
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

	/**
	 * Empties the lobby team and forgets everybody, for a server that has just started.
	 *
	 * <p>The team is stored in the world, so it outlives the run that filled it. A crash while
	 * people were waiting would otherwise leave them on it for good - no name tag, no collision -
	 * long after the lobby let them go.
	 */
	public static void resetOnStartup(MinecraftServer server) {
		forgetEveryone();
		Parkour.forgetEveryone();

		Scoreboard scoreboard = server.getScoreboard();
		Team team = scoreboard.getTeam(TEAM);

		if (team == null) {
			return;
		}

		List<String> stale = new ArrayList<>(team.getPlayerList());

		for (String name : stale) {
			scoreboard.removeScoreHolderFromTeam(name, team);
		}

		if (!stale.isEmpty()) {
			FreezeMute.LOGGER.info("Lobby: took {} player(s) off the lobby team left over from last run",
					stale.size());
		}
	}

	/** Wipes every runtime trace of the lobby. */
	public static void forgetEveryone() {
		MEMBERS.clear();
		MEMBER_ENTITY_IDS.clear();
		PENDING.clear();
		SETTLING.clear();
		anyMembers = false;

		for (ServerBossBar bar : BARS.values()) {
			bar.clearPlayers();
		}

		BARS.clear();
	}
}
