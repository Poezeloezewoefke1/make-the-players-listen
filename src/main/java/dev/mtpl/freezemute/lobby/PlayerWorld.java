package dev.mtpl.freezemute.lobby;

import java.lang.reflect.Method;

import dev.mtpl.freezemute.FreezeMute;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * "Which world is this player in?", asked in a way that survives Yarn renaming things.
 *
 * <p>Yarn moved this accessor around during 1.21.11 - it has been called {@code getWorld} and
 * {@code getServerWorld}, and the one on {@code Entity} became {@code getEntityWorld} - so writing
 * either name down compiles against one mappings build and fails against the next. The
 * intermediary names underneath do not move: they are fixed per Minecraft version and are what the
 * mod is remapped onto at runtime anyway. So the name is resolved through the loader's mapping
 * resolver and the method is looked up once, exactly the way the enchantment registry and the
 * floating-tick reset already are.
 *
 * <p>Two candidates are tried, the specific one first. If neither is there the lobby says nobody is
 * in it rather than guessing, and says so loudly at startup.
 */
public final class PlayerWorld {
	private static final Method LOOKUP = find();

	private PlayerWorld() {
	}

	/** The world a player is standing in, or null if this build hid it from us. */
	public static ServerWorld of(ServerPlayerEntity player) {
		if (LOOKUP == null || player == null) {
			return null;
		}

		try {
			return (ServerWorld) LOOKUP.invoke(player);
		} catch (ReflectiveOperationException | ClassCastException exception) {
			return null;
		}
	}

	public static boolean available() {
		return LOOKUP != null;
	}

	/** Logged once at startup, so a mappings change shows up in the log and not as a dead lobby. */
	public static void logStatus() {
		if (LOOKUP != null) {
			FreezeMute.LOGGER.info("Lobby: player world lookup is on ({})", LOOKUP.getName());
		} else {
			FreezeMute.LOGGER.error("Lobby: player world lookup is off - this Minecraft build names it "
					+ "something the mod does not know, so the lobby cannot tell who is in it. Freezing, "
					+ "muting, kits and voice chat are unaffected.");
		}
	}

	private static Method find() {
		MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();

		// ServerPlayerEntity#getServerWorld, which returns the ServerWorld directly.
		Method method = lookUp(resolver, "net.minecraft.class_3222", "method_51469",
				"()Lnet/minecraft/class_3218;");

		if (method != null) {
			return method;
		}

		// Entity#getWorld, which returns a World that is always a ServerWorld on a server.
		return lookUp(resolver, "net.minecraft.class_1297", "method_73183", "()Lnet/minecraft/class_1937;");
	}

	private static Method lookUp(MappingResolver resolver, String owner, String intermediary, String descriptor) {
		try {
			String name = resolver.mapMethodName("intermediary", owner, intermediary, descriptor);
			Method method = ServerPlayerEntity.class.getMethod(name);
			return ServerWorld.class.isAssignableFrom(method.getReturnType())
					|| method.getReturnType().isAssignableFrom(ServerWorld.class) ? method : null;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}
	}
}
