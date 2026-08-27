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
 * Rolls the enchantments for a kit.
 *
 * <p>Every enchanted piece gets its main enchantment - Protection on armour, Sharpness on a
 * weapon, Efficiency on a digging tool - and then side enchantments on top of it, so a diamond
 * helmet reads as Protection III and Aqua Affinity rather than as whichever one enchantment the
 * shuffle happened to land on.
 *
 * <p>Armour only ever rolls plain Protection and weapons only ever roll Sharpness: the
 * situational variants (Blast, Fire and Projectile Protection, Smite, Bane of Arthropods) are
 * left out so a piece is never quietly worse than it looks. Thorns and Knockback are left out
 * entirely - Knockback because shoving people away is rarely what anybody wants from a kit
 * sword.
 *
 * <p>A tier can also say that the material does not get to cap its pieces, and name enchantments
 * it never hands out - which is how a diamond kit ends up carrying Protection IV and Unbreaking
 * III with no Mending, Swift Sneak or Soul Speed anywhere in it.
 *
 * <p>Mending, Swift Sneak and Soul Speed are top tier only, which in practice means netherite
 * pieces: a diamond kit, and the diamond half of a mixed one, never repairs itself.
 *
 * <p>How good the rolls are follows the piece. The lower tiers roll their levels at random and
 * take one or two sides; netherite does not roll at all - it gets its main enchantment and every
 * side its item can carry, all at maximum level, because it is the top of the ladder.
 *
 * <p>Enchantments are looked up in the server's registry by id, so this keeps working with
 * data pack enchantments and does not depend on constants the mappings do not name.
 */
public final class KitEnchantments {
	/**
	 * One enchantment a piece may roll on top of its main one.
	 *
	 * @param id the enchantment's registry id
	 * @param group enchantments that cannot sit on the same item, or null when it conflicts with
	 *     nothing - only one option per group is ever applied, so a pickaxe never ends up with
	 *     both Silk Touch and Fortune the way a plain random pick would allow
	 * @param maxLevel the highest level it may roll here; below the top tier the level is random
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

	/** Enchants one piece of gear: its main enchantment first, then whatever sides it rolls. */
	public static void apply(MinecraftServer server, ItemStack stack, String itemId, KitTier tier, Random random) {
		EnchantPower power = tier.enchantPowerFor(itemId);

		if (power == EnchantPower.NONE) {
			return;
		}

		Registry<Enchantment> registry = registry(server);

		if (registry == null) {
			return;
		}

		Set<String> usedGroups = new HashSet<>();
		String main = primaryFor(itemId);

		// A tier can ban its own main enchantment; nothing forces it back on.
		if (main != null && tier.allows(main)) {
			enchant(registry, stack, main, mainLevel(main, power, random));
		}

		List<Option> sides = new ArrayList<>(sidePool(itemId, power, tier.bannedEnchantments()));

		// The top tier is not a roll: it takes its whole pool, in the order the pool declares,
		// so "best" means the same thing every time rather than whatever the shuffle allowed.
		if (power != EnchantPower.BEST) {
			Collections.shuffle(sides, random);
		}

		int wanted = power == EnchantPower.BEST ? sides.size() : sideCount(power, random);
		int applied = 0;

		for (Option side : sides) {
			if (applied >= wanted) {
				break;
			}

			if (side.group() != null && !usedGroups.add(side.group())) {
				continue;
			}

			if (enchant(registry, stack, side.id(), sideLevel(side.maxLevel(), power, random))) {
				applied++;
			}
		}
	}

	/**
	 * The enchantment a piece is never without. Everything else is a side enchantment on top, so
	 * an enchanted chestplate always actually protects and an enchanted sword always actually
	 * hits harder.
	 */
	public static String primaryFor(String itemId) {
		if (itemId.endsWith("_helmet") || itemId.endsWith("_chestplate") || itemId.endsWith("_leggings")
				|| itemId.endsWith("_boots")) {
			return "protection";
		}

		if (itemId.endsWith("_sword") || itemId.endsWith("_axe")) {
			return "sharpness";
		}

		if (itemId.endsWith("_pickaxe") || itemId.endsWith("_shovel")) {
			return "efficiency";
		}

		if (itemId.equals(KitTier.SHIELD)) {
			return "unbreaking";
		}

		return null;
	}

	/** The side enchantment ids this item can carry at this power, in order of preference. */
	public static List<String> sideIds(String itemId, EnchantPower power) {
		return sideIds(itemId, power, Set.of());
	}

	/** The same, for a tier that keeps some enchantments off the table. */
	public static List<String> sideIds(String itemId, EnchantPower power, Set<String> banned) {
		List<String> ids = new ArrayList<>();

		for (Option option : sidePool(itemId, power, banned)) {
			ids.add(option.id());
		}

		return ids;
	}

	/** How strong the main enchantment comes out. The better tiers do not roll for it at all. */
	private static int mainLevel(String id, EnchantPower power, Random random) {
		int cap = switch (id) {
			case "protection" -> cap(power, 1, 2, 3, 4);
			case "sharpness" -> cap(power, 1, 2, 3, 5);
			case "efficiency" -> cap(power, 2, 3, 4, 5);
			default -> cap(power, 1, 2, 3, 3);
		};

		if (power.atLeast(EnchantPower.GOOD)) {
			return cap;
		}

		// Best of two rolls, so the main enchantment leans high even on the scrappier kits.
		return Math.max(1 + random.nextInt(cap), 1 + random.nextInt(cap));
	}

	/** How many side enchantments to try for. The top tier takes the lot instead. */
	private static int sideCount(EnchantPower power, Random random) {
		return switch (power) {
			case WEAK -> random.nextInt(2);
			case MIXED -> 1 + random.nextInt(2);
			case GOOD -> 1 + random.nextInt(3);
			default -> 0;
		};
	}

	/** Side levels are rolled below the top tier, and simply maxed at it. */
	private static int sideLevel(int maxLevel, EnchantPower power, Random random) {
		int cap = Math.max(1, maxLevel);

		if (power == EnchantPower.BEST) {
			return cap;
		}

		int rolled = 1 + random.nextInt(cap);
		return power == EnchantPower.GOOD ? Math.max(rolled, 1 + random.nextInt(cap)) : rolled;
	}

	/**
	 * What this item can carry besides its main enchantment. The pool is built per item so a
	 * sword never gets Feather Falling, and it widens as the tier goes up, so better gear draws
	 * on more than just bigger numbers. Declaration order is preference order: at the top tier
	 * the pool is taken as written, so the first of two conflicting options wins.
	 */
	private static List<Option> sidePool(String itemId, EnchantPower power, Set<String> banned) {
		List<Option> options = new ArrayList<>();

		boolean helmet = itemId.endsWith("_helmet");
		boolean leggings = itemId.endsWith("_leggings");
		boolean boots = itemId.endsWith("_boots");
		boolean armour = helmet || leggings || boots || itemId.endsWith("_chestplate");

		boolean sword = itemId.endsWith("_sword");
		boolean axe = itemId.endsWith("_axe");
		boolean digger = itemId.endsWith("_pickaxe") || itemId.endsWith("_shovel") || axe;

		if (helmet) {
			add(options, power, "aqua_affinity", null, EnchantPower.MIXED, 1);
			add(options, power, "respiration", null, EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
		}

		if (boots) {
			add(options, power, "feather_falling", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 4));
			add(options, power, "depth_strider", "walking", EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
			add(options, power, "soul_speed", null, EnchantPower.BEST, 3);
			add(options, power, "frost_walker", "walking", EnchantPower.GOOD, cap(power, 1, 1, 2, 2));
		}

		if (leggings) {
			add(options, power, "swift_sneak", null, EnchantPower.BEST, 3);
		}

		if (sword) {
			add(options, power, "looting", null, EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
			add(options, power, "fire_aspect", null, EnchantPower.MIXED, cap(power, 1, 1, 2, 2));
		}

		if (digger) {
			add(options, power, "fortune", "digging", EnchantPower.MIXED, cap(power, 1, 2, 3, 3));
			add(options, power, "silk_touch", "digging", EnchantPower.GOOD, 1);
		}

		if (axe) {
			add(options, power, "efficiency", null, EnchantPower.WEAK, cap(power, 2, 3, 4, 5));
		}

		if (armour || sword || digger || itemId.equals(KitTier.SHIELD)) {
			add(options, power, "unbreaking", null, EnchantPower.WEAK, cap(power, 1, 2, 3, 3));
			// Top tier only, the same as Swift Sneak and Soul Speed: in practice that means
			// netherite pieces and nothing else, so diamond gear wears out for good.
			add(options, power, "mending", null, EnchantPower.BEST, 1);
		}

		// A shield's main enchantment is Unbreaking, which is also in the list above; nothing
		// should be able to turn up as both the main enchantment and a side.
		String main = primaryFor(itemId);
		options.removeIf(option -> option.id().equals(main));

		options.removeIf(option -> banned.contains(option.id()));

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

	/** Applies one enchantment, or reports that this version does not have it. */
	private static boolean enchant(Registry<Enchantment> registry, ItemStack stack, String id, int level) {
		Optional<? extends RegistryEntry<Enchantment>> entry = registry.getEntry(Identifier.ofVanilla(id));

		if (entry.isEmpty()) {
			return false;
		}

		stack.addEnchantment(entry.get(), level);
		return true;
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
