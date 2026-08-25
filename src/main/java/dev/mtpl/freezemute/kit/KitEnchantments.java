package dev.mtpl.freezemute.kit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.kit.KitTier.EnchantPower;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/**
 * Rolls the random enchantments for a kit.
 *
 * <p>Nothing here is fixed. Which enchantments a piece gets is drawn from a pool that suits the
 * item, how many it gets is rolled, and every level is rolled inside a range rather than handed
 * out at maximum - so a diamond chestplate might come back with Protection II and Thorns I, or
 * with Blast Protection III and Mending, or with nothing much at all.
 *
 * <p>Enchantments are looked up in the server's registry by id, so this keeps working with
 * data pack enchantments and does not depend on constants the mappings do not name.
 */
public final class KitEnchantments {
	/**
	 * One enchantment a piece may roll.
	 *
	 * @param id the enchantment's registry id
	 * @param group enchantments that cannot sit on the same item, or null when it conflicts with
	 *     nothing - only one option per group is ever applied, so a pickaxe never ends up with
	 *     both Silk Touch and Fortune the way a plain random pick would allow
	 * @param maxLevel the highest level it may roll here; the level itself is random below that
	 */
	private record Option(String id, String group, int maxLevel) {
	}

	private static final RegistryKey<Registry<Enchantment>> ENCHANTMENT_REGISTRY =
			RegistryKey.ofRegistry(Identifier.ofVanilla("enchantment"));

	/**
	 * {@code DynamicRegistryManager#get(RegistryKey)}.
	 *
	 * <p>Enchantments are a dynamic registry, so they have to be looked up through the server's
	 * registry manager - but the Yarn build this mod compiles against does not give that method
	 * a readable name, so there is nothing to call in source. It is resolved here through the
	 * intermediary name instead, which is what the game uses at runtime anyway and is fixed for
	 * a given Minecraft version. If it ever cannot be found, kits are still handed out, just
	 * without enchantments.
	 */
	private static final Method REGISTRY_LOOKUP = findRegistryLookup();

	private KitEnchantments() {
	}

	/**
	 * Says at startup whether kits will be enchanted, so a mapping change that breaks the lookup
	 * shows up in the log rather than as quietly plain gear.
	 */
	public static void logStatus() {
		if (REGISTRY_LOOKUP == null) {
			FreezeMute.LOGGER.warn("Kit enchantments are off - the enchantment registry lookup could not be found");
		} else {
			FreezeMute.LOGGER.info("Kit enchantments are on");
		}
	}

	/**
	 * Enchants one piece of gear, or leaves it plain. How many enchantments it gets and how
	 * strong they roll follows the power of the piece, which is the tier and its material
	 * together.
	 */
	public static void apply(MinecraftServer server, ItemStack stack, String itemId, EnchantPower power, Random random) {
		if (power == EnchantPower.NONE) {
			return;
		}

		List<Option> pool = pool(itemId, power);

		if (pool.isEmpty()) {
			return;
		}

		int wanted = count(power, random);

		if (wanted <= 0) {
			return;
		}

		Registry<Enchantment> registry = registry(server);

		if (registry == null) {
			return;
		}

		List<Option> shuffled = new ArrayList<>(pool);
		Collections.shuffle(shuffled, random);

		Set<String> usedGroups = new HashSet<>();
		int applied = 0;

		for (Option option : shuffled) {
			if (applied >= wanted) {
				break;
			}

			if (option.group() != null && !usedGroups.add(option.group())) {
				continue;
			}

			Optional<? extends RegistryEntry<Enchantment>> entry =
					registry.getEntry(Identifier.ofVanilla(option.id()));

			if (entry.isEmpty()) {
				continue;
			}

			stack.addEnchantment(entry.get(), level(option.maxLevel(), power, random));
			applied++;
		}
	}

	/** How many enchantments to try for. The weaker tiers may well roll none at all. */
	private static int count(EnchantPower power, Random random) {
		return switch (power) {
			case WEAK -> random.nextInt(3);
			case MIXED -> 1 + random.nextInt(2);
			case GOOD -> 1 + random.nextInt(3);
			case BEST -> 2 + random.nextInt(3);
			default -> 0;
		};
	}

	/**
	 * Rolls a level between one and the cap. The better kits roll twice and keep the higher one,
	 * so netherite gear leans towards the top of its range without ever being guaranteed it.
	 */
	private static int level(int maxLevel, EnchantPower power, Random random) {
		int cap = Math.max(1, maxLevel);
		int rolled = 1 + random.nextInt(cap);

		if (power.atLeast(EnchantPower.GOOD)) {
			rolled = Math.max(rolled, 1 + random.nextInt(cap));
		}

		return rolled;
	}

	/**
	 * What this item could roll at this power. The pool is built per item so a sword never gets
	 * Feather Falling, and every option carries the weakest kit that may see it, so the better
	 * gear draws from a visibly wider pool rather than just higher numbers.
	 */
	private static List<Option> pool(String itemId, EnchantPower power) {
		List<Option> options = new ArrayList<>();

		boolean helmet = itemId.endsWith("_helmet");
		boolean chestplate = itemId.endsWith("_chestplate");
		boolean leggings = itemId.endsWith("_leggings");
		boolean boots = itemId.endsWith("_boots");
		boolean armour = helmet || chestplate || leggings || boots;

		boolean sword = itemId.endsWith("_sword");
		boolean axe = itemId.endsWith("_axe");
		boolean digger = itemId.endsWith("_pickaxe") || itemId.endsWith("_shovel") || axe;

		if (armour) {
			// Plain Protection only. Blast, Fire and Projectile Protection are deliberately left
			// out so armour is never worse than it looks against ordinary damage.
			add(options, power, "protection", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 4));
			add(options, power, "unbreaking", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 3));
			add(options, power, "thorns", null, EnchantPower.GOOD, cap(power, 1, 1, 2, 3));
			add(options, power, "mending", null, EnchantPower.GOOD, 1);
		}

		if (helmet) {
			add(options, power, "aqua_affinity", null, EnchantPower.MIXED, 1);
			add(options, power, "respiration", null, EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
		}

		if (boots) {
			add(options, power, "feather_falling", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 4));
			add(options, power, "depth_strider", "walking", EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
			add(options, power, "frost_walker", "walking", EnchantPower.GOOD, cap(power, 1, 1, 2, 2));
			add(options, power, "soul_speed", null, EnchantPower.BEST, 3);
		}

		if (leggings) {
			add(options, power, "swift_sneak", null, EnchantPower.BEST, 3);
		}

		if (sword || axe) {
			// Sharpness only. Smite and Bane of Arthropods are left out so a weapon is never
			// carrying damage that only helps against one kind of mob.
			add(options, power, "sharpness", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 5));
		}

		if (sword) {
			add(options, power, "knockback", null, EnchantPower.WEAK, cap(power, 1, 1, 2, 2));
			add(options, power, "fire_aspect", null, EnchantPower.MIXED, cap(power, 1, 1, 2, 2));
			add(options, power, "looting", null, EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
			add(options, power, "unbreaking", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 3));
			add(options, power, "mending", null, EnchantPower.GOOD, 1);
		}

		if (digger) {
			add(options, power, "efficiency", null, EnchantPower.WEAK, cap(power, 2, 3, 4, 5));
			add(options, power, "fortune", "digging", EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
			add(options, power, "silk_touch", "digging", EnchantPower.GOOD, 1);
			add(options, power, "unbreaking", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 3));
			add(options, power, "mending", null, EnchantPower.GOOD, 1);
		}

		if (itemId.equals(KitTier.SHIELD)) {
			add(options, power, "unbreaking", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 3));
			add(options, power, "mending", null, EnchantPower.GOOD, 1);
		}

		return options;
	}

	/** Adds an option, unless this kit is too poor to have seen that enchantment at all. */
	private static void add(List<Option> options, EnchantPower power, String id, String group,
			EnchantPower minPower, int maxLevel) {
		if (power.atLeast(minPower)) {
			options.add(new Option(id, group, maxLevel));
		}
	}

	/** The level cap for the current power: one number per step from weak to best. */
	private static int cap(EnchantPower power, int weak, int mixed, int good, int best) {
		return switch (power) {
			case WEAK -> weak;
			case MIXED -> mixed;
			case GOOD -> good;
			case BEST -> best;
			default -> 0;
		};
	}

	@SuppressWarnings("unchecked")
	private static Registry<Enchantment> registry(MinecraftServer server) {
		if (REGISTRY_LOOKUP == null) {
			return null;
		}

		try {
			return (Registry<Enchantment>) REGISTRY_LOOKUP.invoke(server.getRegistryManager(), ENCHANTMENT_REGISTRY);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}
	}

	private static Method findRegistryLookup() {
		try {
			String name = FabricLoader.getInstance().getMappingResolver().mapMethodName(
					"intermediary", "net.minecraft.class_5455", "method_30530",
					"(Lnet/minecraft/class_5321;)Lnet/minecraft/class_2378;");
			Method method = DynamicRegistryManager.class.getMethod(name, RegistryKey.class);
			method.setAccessible(true);
			return method;
		} catch (Throwable throwable) {
			FreezeMute.LOGGER.warn("Could not find the registry lookup ({}), so kits will be handed out without enchantments",
					throwable.toString());
			return null;
		}
	}
}
