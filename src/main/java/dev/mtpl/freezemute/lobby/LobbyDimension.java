package dev.mtpl.freezemute.lobby;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FreezeMuteConfig;

import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The {@code astra:lobby} dimension.
 *
 * <p>Minecraft only builds dimensions that a data pack describes, and this mod deliberately
 * depends on Fabric Loader alone, so it cannot ship the description inside its own jar the way a
 * Fabric API mod would. Instead it writes a tiny data pack into the world folder on every start.
 * Mod initialisation runs before the server loads the level, so the pack is already on disk when
 * the dimensions are built and the lobby is normally there on the very start that installs it -
 * and staying "one jar and nothing else" remains true: nobody has to copy a file anywhere. If the
 * pack does land too late on some setup, one restart is the whole fix, which is why the commands
 * say so rather than failing quietly.
 *
 * <p>The dimension itself is empty on purpose. A void world with a stone platform under the spawn
 * is the right canvas for a lobby: no mobs, no weather worth mentioning, permanent noon, and real
 * void under a parkour course so a missed jump has somewhere to fall.
 */
public final class LobbyDimension {
	public static final String NAMESPACE = "astra";
	public static final String PATH = "lobby";
	public static final RegistryKey<World> KEY =
			RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, PATH));

	/** Where a fresh lobby puts people, until staff run {@code /lobby setspawn}. */
	public static final Spot DEFAULT_SPAWN = new Spot(0.5D, 65.0D, 0.5D, 0.0F, 0.0F);
	private static final String PACK_NAME = "astra_lobby";

	private static volatile boolean platformChecked;
	private static volatile boolean missingWarned;
	private static volatile boolean reported;

	private LobbyDimension() {
	}

	/**
	 * Writes the data pack, replacing whatever was there before so an update to the mod is also an
	 * update to the pack. Runs during mod initialisation, before the level is loaded.
	 */
	public static void install(Path gameDirectory) {
		if (!FreezeMuteConfig.get().lobbyInstallDimension) {
			FreezeMute.LOGGER.info("Lobby dimension: not installing, lobbyInstallDimension is off");
			return;
		}

		Path root = gameDirectory.resolve(levelName(gameDirectory)).resolve("datapacks").resolve(PACK_NAME);

		try {
			Files.createDirectories(root.resolve("data").resolve(NAMESPACE).resolve("dimension"));
			Files.createDirectories(root.resolve("data").resolve(NAMESPACE).resolve("dimension_type"));

			write(root.resolve("pack.mcmeta"), PACK_MCMETA);
			write(root.resolve("data").resolve(NAMESPACE).resolve("dimension").resolve(PATH + ".json"), DIMENSION);
			write(root.resolve("data").resolve(NAMESPACE).resolve("dimension_type").resolve(PATH + ".json"), DIMENSION_TYPE);

			FreezeMute.LOGGER.info("Lobby dimension: data pack written to {}", root);
		} catch (IOException exception) {
			FreezeMute.LOGGER.error("Lobby dimension: could not write the data pack to {}", root, exception);
		}
	}

	/**
	 * Says once, on the first tick, whether the dimension made it. Worth a line in the log: it is
	 * the one part of the lobby that depends on something outside this mod's control.
	 */
	public static void reportOnce(MinecraftServer server) {
		if (reported) {
			return;
		}

		reported = true;

		if (world(server) != null) {
			FreezeMute.LOGGER.info("Lobby dimension: {}:{} is ready", NAMESPACE, PATH);
		}
	}

	/**
	 * The lobby world, or null when the server has not been restarted since the data pack appeared.
	 * The warning is logged once so a server without the dimension does not spam its log.
	 */
	public static ServerWorld world(MinecraftServer server) {
		if (server == null) {
			return null;
		}

		ServerWorld world = server.getWorld(KEY);

		if (world == null && !missingWarned) {
			missingWarned = true;
			FreezeMute.LOGGER.warn("Lobby dimension: {}:{} does not exist yet. Restart the server once - the data "
					+ "pack is written before the level loads, so the next start creates it.", NAMESPACE, PATH);
		}

		return world;
	}

	/**
	 * Lays a stone platform under the spawn the first time the lobby is used, so the first person
	 * through the door does not drop straight into the void. Runs once per server run, and only
	 * when the block is still air, so anything staff build afterwards is left alone.
	 */
	public static void ensurePlatform(ServerWorld world, Spot spawn) {
		if (platformChecked || world == null) {
			return;
		}

		platformChecked = true;

		FreezeMuteConfig config = FreezeMuteConfig.get();

		if (!config.lobbySpawnPlatform) {
			return;
		}

		int centreX = (int) Math.floor(spawn.x());
		int centreZ = (int) Math.floor(spawn.z());
		int floor = (int) Math.floor(spawn.y()) - 1;

		if (!world.getBlockState(new BlockPos(centreX, floor, centreZ)).isAir()) {
			return;
		}

		int radius = Math.max(1, Math.min(64, config.lobbyPlatformRadius));
		int placed = 0;

		for (int x = centreX - radius; x <= centreX + radius; x++) {
			for (int z = centreZ - radius; z <= centreZ + radius; z++) {
				world.setBlockState(new BlockPos(x, floor, z), Blocks.SMOOTH_STONE.getDefaultState());
				placed++;
			}
		}

		FreezeMute.LOGGER.info("Lobby dimension: laid a {}x{} platform at {} {} {}",
				radius * 2 + 1, radius * 2 + 1, centreX, floor, centreZ);
		FreezeMute.LOGGER.info("Lobby dimension: {} block(s) placed", placed);
	}

	/** Reads {@code level-name} out of server.properties, because that is where the world folder is. */
	private static String levelName(Path gameDirectory) {
		Path properties = gameDirectory.resolve("server.properties");

		if (Files.isRegularFile(properties)) {
			Properties values = new Properties();

			try (var reader = Files.newBufferedReader(properties, StandardCharsets.UTF_8)) {
				values.load(reader);
				String name = values.getProperty("level-name");

				if (name != null && !name.isBlank()) {
					return name.trim();
				}
			} catch (IOException | RuntimeException exception) {
				FreezeMute.LOGGER.warn("Lobby dimension: could not read server.properties, assuming the world "
						+ "folder is called 'world'");
			}
		}

		return "world";
	}

	private static void write(Path file, String content) throws IOException {
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	/**
	 * {@code supported_formats} is deliberately wide. The pack format number changes with almost
	 * every Minecraft release and a pack the server thinks is incompatible is not enabled, which
	 * would silently take the dimension away. The pack contains nothing that can go stale, so
	 * claiming to support every format is honest rather than reckless.
	 */
	private static final String PACK_MCMETA = """
			{
			  "pack": {
			    "description": "Astra lobby dimension, written by Make The Players Listen",
			    "pack_format": 88,
			    "supported_formats": { "min_inclusive": 4, "max_inclusive": 999999 }
			  }
			}
			""";

	private static final String DIMENSION = """
			{
			  "type": "astra:lobby",
			  "generator": {
			    "type": "minecraft:flat",
			    "settings": {
			      "biome": "minecraft:the_void",
			      "lakes": false,
			      "features": false,
			      "layers": [],
			      "structure_overrides": []
			    }
			  }
			}
			""";

	private static final String DIMENSION_TYPE = """
			{
			  "ultrawarm": false,
			  "natural": false,
			  "piglin_safe": false,
			  "respawn_anchor_works": false,
			  "bed_works": false,
			  "has_raids": false,
			  "has_skylight": true,
			  "has_ceiling": false,
			  "coordinate_scale": 1.0,
			  "ambient_light": 1.0,
			  "fixed_time": 6000,
			  "logical_height": 384,
			  "effects": "minecraft:overworld",
			  "infiniburn": "#minecraft:infiniburn_overworld",
			  "min_y": -64,
			  "height": 384,
			  "monster_spawn_block_light_limit": 0,
			  "monster_spawn_light_level": 0
			}
			""";
}
