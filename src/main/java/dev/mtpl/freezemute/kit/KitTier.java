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

	DIAMOND(15, 50, EnchantPower.MIXED,
			List.of("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"),
			List.of("diamond_sword", "diamond_pickaxe", "diamond_axe", "diamond_shovel"),
			List.of(entry("golden_apple", 1, 3), entry("cooked_beef", 8, 16)),
			List.of(entry("obsidian", 2, 8), entry("cobblestone", 32, 64), entry("torch", 16, 32)),
			List.of(entry("diamond", 1, 4), entry("bow", 0, 1), entry("arrow", 16, 48),
					entry("water_bucket", 0, 1), entry("shield", 0, 1), entry("ender_pearl", 2, 6))),

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

	/** How strong the random enchantments are, if there are any. */
	public enum EnchantPower {
		NONE,
		WEAK,
		MIXED,
		GOOD
	}

	private final int minWearPercent;
	private final int maxWearPercent;
	private final EnchantPower enchantPower;
	private final List<String> armour;
	private final List<String> tools;
	private final List<Stack> food;
	private final List<Stack> blocks;
	private final List<Stack> oddsAndEnds;

	KitTier(int minWearPercent, int maxWearPercent, EnchantPower enchantPower, List<String> armour,
			List<String> tools, List<Stack> food, List<Stack> blocks, List<Stack> oddsAndEnds) {
		this.minWearPercent = minWearPercent;
		this.maxWearPercent = maxWearPercent;
		this.enchantPower = enchantPower;
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

	public EnchantPower enchantPower() {
		return enchantPower;
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
