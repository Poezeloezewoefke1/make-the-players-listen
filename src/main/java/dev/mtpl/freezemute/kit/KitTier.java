package dev.mtpl.freezemute.kit;

import java.util.List;
import java.util.Locale;

/**
 * The gear tiers {@code /kitgive} can hand out.
 *
 * <p>Everything is written as an item id rather than an {@code Items} constant on purpose: the
 * mappings for 1.21.11 do not name the tool, armour and enchantment constants, and ids are also
 * what a server owner would recognise.
 *
 * <p>Wear is how used the gear looks, as a percentage of its durability, picked at random per
 * item - a poor player's kit is close to falling apart, a netherite one has seen a few fights.
 */
public enum KitTier {
	POOR(45, 90, EnchantPower.NONE,
			List.of("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
			List.of("stone_sword", "stone_pickaxe", "stone_axe", "stone_shovel"),
			List.of(entry("bread", 3, 8), entry("apple", 0, 4)),
			List.of(entry("cobblestone", 16, 48), entry("oak_planks", 8, 24), entry("torch", 4, 16)),
			List.of(entry("stick", 1, 6), entry("string", 0, 4), entry("coal", 1, 6),
					entry("rotten_flesh", 0, 5), entry("wooden_hoe", 0, 1))),

	COPPER(35, 80, EnchantPower.NONE,
			List.of("chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"),
			List.of("stone_sword", "iron_pickaxe", "stone_axe", "iron_shovel"),
			List.of(entry("cooked_chicken", 4, 10), entry("bread", 2, 6)),
			List.of(entry("cobblestone", 24, 64), entry("copper_block", 1, 4), entry("torch", 8, 20)),
			List.of(entry("copper_ingot", 4, 16), entry("raw_copper", 0, 8), entry("lightning_rod", 0, 1),
					entry("bucket", 0, 1))),

	IRON(25, 65, EnchantPower.WEAK,
			List.of("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"),
			List.of("iron_sword", "iron_pickaxe", "iron_axe", "iron_shovel"),
			List.of(entry("cooked_beef", 4, 10), entry("bread", 2, 8)),
			List.of(entry("cobblestone", 32, 64), entry("torch", 8, 24), entry("oak_planks", 8, 32)),
			List.of(entry("iron_ingot", 2, 8), entry("bucket", 0, 1), entry("shield", 0, 1),
					entry("bow", 0, 1), entry("arrow", 8, 32), entry("ender_pearl", 0, 2))),

	/** Half kitted out: a diamond sword and pickaxe over mostly iron armour. */
	IRON_DIAMOND(20, 58, EnchantPower.MIXED,
			List.of("iron_helmet", "diamond_chestplate", "iron_leggings", "diamond_boots"),
			List.of("diamond_sword", "diamond_pickaxe", "iron_axe", "iron_shovel"),
			List.of(entry("cooked_beef", 6, 12), entry("golden_apple", 0, 2)),
			List.of(entry("cobblestone", 32, 64), entry("torch", 12, 28), entry("obsidian", 0, 4)),
			List.of(entry("iron_ingot", 2, 8), entry("diamond", 0, 3), entry("bow", 0, 1),
					entry("arrow", 12, 40), entry("shield", 0, 1), entry("ender_pearl", 0, 4))),

	DIAMOND(15, 50, EnchantPower.GOOD,
			List.of("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"),
			List.of("diamond_sword", "diamond_pickaxe", "diamond_axe", "diamond_shovel"),
			List.of(entry("golden_apple", 1, 3), entry("cooked_beef", 8, 16)),
			List.of(entry("obsidian", 2, 8), entry("cobblestone", 32, 64), entry("torch", 16, 32)),
			List.of(entry("diamond", 1, 4), entry("bow", 0, 1), entry("arrow", 16, 48),
					entry("water_bucket", 0, 1), entry("shield", 0, 1), entry("ender_pearl", 2, 6))),

	/** Most of the way there: netherite where it counts, diamond for the rest. */
	DIAMOND_NETHERITE(10, 42, EnchantPower.GOOD,
			List.of("diamond_helmet", "netherite_chestplate", "diamond_leggings", "netherite_boots"),
			List.of("netherite_sword", "diamond_pickaxe", "netherite_axe", "diamond_shovel"),
			List.of(entry("golden_apple", 2, 5), entry("enchanted_golden_apple", 0, 1), entry("cooked_beef", 8, 16)),
			List.of(entry("obsidian", 4, 12), entry("cobblestone", 48, 64), entry("torch", 16, 32)),
			List.of(entry("netherite_scrap", 0, 2), entry("diamond", 2, 6), entry("crossbow", 0, 1),
					entry("arrow", 16, 48), entry("shield", 0, 1), entry("ender_pearl", 3, 8))),

	NETHERITE(5, 35, EnchantPower.GOOD,
			List.of("netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"),
			List.of("netherite_sword", "netherite_pickaxe", "netherite_axe", "diamond_shovel"),
			List.of(entry("enchanted_golden_apple", 1, 2), entry("golden_apple", 2, 6), entry("cooked_beef", 8, 16)),
			List.of(entry("obsidian", 8, 16), entry("cobblestone", 64, 64), entry("torch", 16, 32)),
			List.of(entry("totem_of_undying", 0, 1), entry("netherite_scrap", 0, 2), entry("crossbow", 0, 1),
					entry("arrow", 16, 64), entry("shield", 0, 1), entry("ender_pearl", 4, 12)));

	/** An item id with how many of it to hand out. A minimum of 0 means "maybe not at all". */
	public record Stack(String id, int min, int max) {
	}

	/**
	 * How strong random enchantments may get. A tier sets the ceiling and the material of each
	 * piece decides the rest, so on a mixed kit the iron parts stay scrappy while the diamond
	 * parts get the better rolls.
	 */
	public enum EnchantPower {
		NONE,
		WEAK,
		MIXED,
		GOOD;

		/** The weaker of two powers. */
		public EnchantPower min(EnchantPower other) {
			return ordinal() <= other.ordinal() ? this : other;
		}

		/**
		 * The most a piece of gear can carry, judged by what it is made of. The tier's ceiling
		 * still applies on top, so diamond rolls middling enchantments on the half-iron tier and
		 * good ones once the whole kit is diamond.
		 */
		public static EnchantPower ofMaterial(String itemId) {
			if (itemId.startsWith("netherite_") || itemId.startsWith("diamond_")) {
				return GOOD;
			}

			if (itemId.startsWith("iron_") || itemId.startsWith("chainmail_")) {
				return WEAK;
			}

			if (itemId.startsWith("stone_") || itemId.startsWith("wooden_") || itemId.startsWith("leather_")
					|| itemId.startsWith("golden_")) {
				return NONE;
			}

			// Bows, crossbows and shields have no material tier of their own, so the tier decides.
			return GOOD;
		}
	}

	private final int minWearPercent;
	private final int maxWearPercent;
	private final EnchantPower enchantCeiling;
	private final List<String> armour;
	private final List<String> tools;
	private final List<Stack> food;
	private final List<Stack> blocks;
	private final List<Stack> oddsAndEnds;

	KitTier(int minWearPercent, int maxWearPercent, EnchantPower enchantPower, List<String> armour,
			List<String> tools, List<Stack> food, List<Stack> blocks, List<Stack> oddsAndEnds) {
		this.minWearPercent = minWearPercent;
		this.maxWearPercent = maxWearPercent;
		this.enchantCeiling = enchantPower;
		this.armour = armour;
		this.tools = tools;
		this.food = food;
		this.blocks = blocks;
		this.oddsAndEnds = oddsAndEnds;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public int minWearPercent() {
		return minWearPercent;
	}

	public int maxWearPercent() {
		return maxWearPercent;
	}

	/** The strongest enchantments this tier allows; a piece may still get less. */
	public EnchantPower enchantCeiling() {
		return enchantCeiling;
	}

	/** What this tier actually rolls for one item, once its material is taken into account. */
	public EnchantPower enchantPowerFor(String itemId) {
		return enchantCeiling.min(EnchantPower.ofMaterial(itemId));
	}

	public List<String> armour() {
		return armour;
	}

	public List<String> tools() {
		return tools;
	}

	public List<Stack> food() {
		return food;
	}

	public List<Stack> blocks() {
		return blocks;
	}

	public List<Stack> oddsAndEnds() {
		return oddsAndEnds;
	}

	private static Stack entry(String id, int min, int max) {
		return new Stack(id, min, max);
	}
}
