package dev.mtpl.freezemute.kit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The gear tiers {@code /kitgive} can hand out.
 *
 * <p>A tier is not a fixed shopping list. It is a set of odds: which materials each armour slot
 * and each tool is likely to be made of, and pools of food, blocks and odds and ends to draw a
 * few entries from. Two players given the same tier therefore get different kits - different
 * pieces, different amounts, different wear and different enchantments.
 *
 * <p>Everything is written as an item id rather than an {@code Items} constant on purpose: the
 * mappings for 1.21.11 do not name the tool, armour and enchantment constants, and ids are also
 * what a server owner would recognise.
 */
public enum KitTier {
	POOR(45, 90, 60, EnchantPower.NONE, null,
			List.of(choice("leather", 8), choice("golden", 1)),
			List.of(choice("stone", 6), choice("wooden", 3), choice("golden", 1)),
			pool(1, 2,
					entry("bread", 2, 6), entry("apple", 1, 4), entry("carrot", 2, 6),
					entry("baked_potato", 2, 6), entry("cooked_cod", 1, 4), entry("rotten_flesh", 3, 8)),
			pool(1, 3,
					entry("cobblestone", 16, 48), entry("oak_planks", 8, 24), entry("dirt", 8, 32),
					entry("torch", 4, 16), entry("oak_log", 2, 8), entry("sand", 8, 24)),
			pool(2, 4,
					entry("stick", 1, 6), entry("string", 1, 4), entry("coal", 1, 6), entry("bone", 1, 4),
					entry("flint", 1, 3), entry("wooden_hoe", 1, 1), entry("leather", 1, 3),
					entry("feather", 1, 4), entry("gunpowder", 1, 2), entry("wheat_seeds", 2, 8),
					entry("oak_sapling", 1, 3), entry("rotten_flesh", 1, 5))),

	COPPER(35, 80, 72, EnchantPower.NONE, null,
			List.of(choice("chainmail", 6), choice("leather", 3), choice("golden", 1)),
			List.of(choice("stone", 5), choice("iron", 3), choice("golden", 1)),
			pool(1, 2,
					entry("cooked_chicken", 4, 10), entry("bread", 2, 6), entry("cooked_porkchop", 3, 8),
					entry("melon_slice", 3, 9), entry("cooked_cod", 3, 8), entry("baked_potato", 4, 10)),
			pool(1, 3,
					entry("cobblestone", 24, 64), entry("copper_block", 1, 4), entry("torch", 8, 20),
					entry("oak_planks", 16, 32), entry("glass", 4, 16), entry("ladder", 4, 12)),
			pool(2, 4,
					entry("copper_ingot", 4, 16), entry("raw_copper", 2, 8), entry("lightning_rod", 1, 1),
					entry("bucket", 1, 1), entry("spyglass", 1, 1), entry("iron_nugget", 3, 9),
					entry("flint_and_steel", 1, 1), entry("compass", 1, 1), entry("copper_bulb", 1, 3),
					entry("brush", 1, 1))),

	IRON(25, 65, 85, EnchantPower.WEAK, null,
			List.of(choice("iron", 8), choice("chainmail", 1)),
			List.of(choice("iron", 8), choice("stone", 1)),
			pool(1, 2,
					entry("cooked_beef", 4, 10), entry("bread", 2, 8), entry("golden_carrot", 2, 6),
					entry("cooked_mutton", 3, 8), entry("pumpkin_pie", 2, 5)),
			pool(1, 3,
					entry("cobblestone", 32, 64), entry("torch", 8, 24), entry("oak_planks", 8, 32),
					entry("iron_block", 1, 2), entry("scaffolding", 8, 16), entry("glass", 8, 24)),
			pool(2, 5,
					entry("iron_ingot", 2, 8), entry("bucket", 1, 1), entry("shield", 1, 1),
					entry("bow", 1, 1), entry("arrow", 8, 32), entry("ender_pearl", 1, 2),
					entry("flint_and_steel", 1, 1), entry("fishing_rod", 1, 1), entry("oak_boat", 1, 1),
					entry("redstone", 4, 12), entry("name_tag", 1, 1))),

	/** Half kitted out: some diamond over iron, and which pieces is up to the roll. */
	IRON_DIAMOND(20, 58, 88, EnchantPower.MIXED, "diamond",
			List.of(choice("iron", 5), choice("diamond", 3)),
			List.of(choice("iron", 4), choice("diamond", 4)),
			pool(1, 2,
					entry("cooked_beef", 6, 12), entry("golden_apple", 1, 2), entry("golden_carrot", 3, 8),
					entry("pumpkin_pie", 3, 6), entry("cooked_mutton", 6, 12)),
			pool(1, 3,
					entry("cobblestone", 32, 64), entry("torch", 12, 28), entry("obsidian", 1, 4),
					entry("oak_planks", 16, 48), entry("iron_block", 1, 3)),
			pool(2, 5,
					entry("iron_ingot", 2, 8), entry("diamond", 1, 3), entry("bow", 1, 1),
					entry("arrow", 12, 40), entry("shield", 1, 1), entry("ender_pearl", 1, 4),
					entry("water_bucket", 1, 1), entry("fishing_rod", 1, 1), entry("flint_and_steel", 1, 1),
					entry("experience_bottle", 2, 8), entry("ender_chest", 1, 1))),

	DIAMOND(15, 50, 92, EnchantPower.GOOD, null,
			List.of(choice("diamond", 8), choice("iron", 1)),
			List.of(choice("diamond", 8), choice("iron", 1)),
			pool(1, 2,
					entry("golden_apple", 1, 3), entry("cooked_beef", 8, 16), entry("golden_carrot", 6, 16),
					entry("pumpkin_pie", 4, 8)),
			pool(1, 3,
					entry("obsidian", 2, 8), entry("cobblestone", 32, 64), entry("torch", 16, 32),
					entry("oak_planks", 32, 64), entry("diamond_block", 1, 1)),
			pool(2, 5,
					entry("diamond", 1, 4), entry("bow", 1, 1), entry("arrow", 16, 48),
					entry("water_bucket", 1, 1), entry("shield", 1, 1), entry("ender_pearl", 2, 6),
					entry("experience_bottle", 4, 16), entry("ender_chest", 1, 1), entry("anvil", 1, 1),
					entry("firework_rocket", 8, 16), entry("lava_bucket", 1, 1), entry("fishing_rod", 1, 1))),

	/** Most of the way there: netherite where the roll landed, diamond for the rest. */
	DIAMOND_NETHERITE(10, 42, 95, EnchantPower.BEST, "netherite",
			List.of(choice("diamond", 5), choice("netherite", 3)),
			List.of(choice("diamond", 4), choice("netherite", 4)),
			pool(1, 2,
					entry("golden_apple", 2, 5), entry("enchanted_golden_apple", 1, 1),
					entry("cooked_beef", 8, 16), entry("golden_carrot", 8, 20)),
			pool(1, 3,
					entry("obsidian", 4, 12), entry("cobblestone", 48, 64), entry("torch", 16, 32),
					entry("ancient_debris", 1, 2), entry("crying_obsidian", 2, 6)),
			pool(2, 5,
					entry("netherite_scrap", 1, 2), entry("diamond", 2, 6), entry("crossbow", 1, 1),
					entry("arrow", 16, 48), entry("shield", 1, 1), entry("ender_pearl", 3, 8),
					entry("totem_of_undying", 1, 1), entry("experience_bottle", 8, 24),
					entry("respawn_anchor", 1, 1), entry("firework_rocket", 16, 32), entry("bow", 1, 1))),

	NETHERITE(5, 35, 100, EnchantPower.BEST, null,
			List.of(choice("netherite", 7), choice("diamond", 1)),
			List.of(choice("netherite", 7), choice("diamond", 1)),
			pool(1, 2,
					entry("enchanted_golden_apple", 1, 2), entry("golden_apple", 2, 6),
					entry("cooked_beef", 8, 16), entry("golden_carrot", 10, 24)),
			pool(1, 3,
					entry("obsidian", 8, 16), entry("cobblestone", 64, 64), entry("torch", 16, 32),
					entry("ancient_debris", 1, 3), entry("crying_obsidian", 4, 12),
					entry("netherite_block", 1, 1)),
			pool(2, 6,
					entry("totem_of_undying", 1, 1), entry("netherite_scrap", 1, 2), entry("crossbow", 1, 1),
					entry("bow", 1, 1), entry("arrow", 16, 64), entry("shield", 1, 1),
					entry("ender_pearl", 4, 12), entry("experience_bottle", 16, 48), entry("elytra", 1, 1),
					entry("firework_rocket", 16, 64), entry("trident", 1, 1), entry("respawn_anchor", 1, 1)));

	/** The armour slots a kit rolls for, in the order they are handed out. */
	public static final List<String> ARMOUR_SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

	/** The tools a kit rolls for. */
	public static final List<String> TOOL_SLOTS = List.of("sword", "pickaxe", "axe", "shovel");

	/** An item id with how many of it to hand out. */
	public record Stack(String id, int min, int max) {
	}

	/** One material a slot may come out as, and how often it should win the roll. */
	public record Choice(String material, int weight) {
	}

	/** A set of things a kit might carry, and how many of them to actually take. */
	public record Pool(List<Stack> options, int minPicks, int maxPicks) {
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
		GOOD,
		BEST;

		/** The weaker of two powers. */
		public EnchantPower min(EnchantPower other) {
			return ordinal() <= other.ordinal() ? this : other;
		}

		/** True when this power is at least as strong as {@code other}. */
		public boolean atLeast(EnchantPower other) {
			return ordinal() >= other.ordinal();
		}

		/**
		 * The most a piece of gear can carry, judged by what it is made of. The tier's ceiling
		 * still applies on top, so diamond rolls middling enchantments on the half-iron tier and
		 * good ones once the whole kit is diamond.
		 */
		public static EnchantPower ofMaterial(String itemId) {
			if (itemId.startsWith("netherite_")) {
				return BEST;
			}

			if (itemId.startsWith("diamond_")) {
				return GOOD;
			}

			if (itemId.startsWith("iron_") || itemId.startsWith("chainmail_")) {
				return WEAK;
			}

			if (itemId.startsWith("stone_") || itemId.startsWith("wooden_") || itemId.startsWith("leather_")
					|| itemId.startsWith("golden_")) {
				return NONE;
			}

			// Bows, shields, tridents and the like have no material tier, so the kit's decides.
			return BEST;
		}
	}

	private final int minWearPercent;
	private final int maxWearPercent;
	private final int presencePercent;
	private final EnchantPower enchantCeiling;
	private final String signature;
	private final List<Choice> armourMaterials;
	private final List<Choice> toolMaterials;
	private final Pool food;
	private final Pool blocks;
	private final Pool oddsAndEnds;

	KitTier(int minWearPercent, int maxWearPercent, int presencePercent, EnchantPower enchantPower,
			String signature, List<Choice> armourMaterials, List<Choice> toolMaterials, Pool food, Pool blocks,
			Pool oddsAndEnds) {
		this.minWearPercent = minWearPercent;
		this.maxWearPercent = maxWearPercent;
		this.presencePercent = presencePercent;
		this.enchantCeiling = enchantPower;
		this.signature = signature;
		this.armourMaterials = armourMaterials;
		this.toolMaterials = toolMaterials;
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

	/** How likely each piece of gear is to be there at all - poorer players have gaps. */
	public int presencePercent() {
		return presencePercent;
	}

	/** The strongest enchantments this tier allows; a piece may still get less. */
	public EnchantPower enchantCeiling() {
		return enchantCeiling;
	}

	/** What this tier actually rolls for one item, once its material is taken into account. */
	public EnchantPower enchantPowerFor(String itemId) {
		return enchantCeiling.min(EnchantPower.ofMaterial(itemId));
	}

	public List<Choice> armourMaterials() {
		return armourMaterials;
	}

	public List<Choice> toolMaterials() {
		return toolMaterials;
	}

	public Pool food() {
		return food;
	}

	public Pool blocks() {
		return blocks;
	}

	public Pool oddsAndEnds() {
		return oddsAndEnds;
	}

	/**
	 * Rolls the item id for one armour slot, e.g. {@code diamond_chestplate}. On the mixed tiers
	 * this is where the half-geared look comes from: each slot is decided on its own, so one
	 * player ends up with the diamond chestplate and the next with diamond boots.
	 */
	public String rollArmour(String slot, Random random) {
		return weighted(armourMaterials, random) + "_" + slot;
	}

	/** Rolls the item id for one tool slot, e.g. {@code netherite_pickaxe}. */
	public String rollTool(String slot, Random random) {
		return weighted(toolMaterials, random) + "_" + slot;
	}

	/**
	 * The material a mixed tier has to show at least one piece of, or null when the tier is made
	 * of one thing anyway.
	 */
	public String signature() {
		return signature;
	}

	/**
	 * Makes sure a mixed tier looks like the tier it is called. Every slot is rolled on its own,
	 * so a diamond_netherite kit can legitimately come up all diamond - which is indistinguishable
	 * from the tier below it. When that happens one piece is promoted, so the kit always carries
	 * at least a trace of what it was asked for.
	 *
	 * @param gearIds the rolled gear ids, edited in place
	 */
	public void ensureSignature(List<String> gearIds, Random random) {
		if (signature == null || gearIds.isEmpty()) {
			return;
		}

		String prefix = signature + "_";

		for (String id : gearIds) {
			if (id.startsWith(prefix)) {
				return;
			}
		}

		int index = random.nextInt(gearIds.size());
		String slot = gearIds.get(index).substring(gearIds.get(index).indexOf('_') + 1);
		gearIds.set(index, prefix + slot);
	}

	/**
	 * Rolls what the kit is carrying besides gear: a couple of the tier's foods, a few of its
	 * blocks and a handful of odds and ends, all drawn at random from bigger pools than any one
	 * kit gets to see.
	 */
	public List<Stack> rollSupplies(Random random) {
		List<Stack> rolled = new ArrayList<>();
		rolled.addAll(pick(food, random));
		rolled.addAll(pick(blocks, random));
		rolled.addAll(pick(oddsAndEnds, random));
		return rolled;
	}

	/** Takes a random handful out of a pool, without taking the same entry twice. */
	public static List<Stack> pick(Pool pool, Random random) {
		List<Stack> options = new ArrayList<>(pool.options());
		Collections.shuffle(options, random);

		int spread = Math.max(1, pool.maxPicks() - pool.minPicks() + 1);
		int wanted = Math.max(0, Math.min(options.size(), pool.minPicks() + random.nextInt(spread)));

		return List.copyOf(options.subList(0, wanted));
	}

	/** Picks one material, respecting how heavily each one is weighted. */
	public static String weighted(List<Choice> choices, Random random) {
		int total = 0;

		for (Choice choice : choices) {
			total += choice.weight();
		}

		int roll = random.nextInt(Math.max(1, total));

		for (Choice choice : choices) {
			roll -= choice.weight();

			if (roll < 0) {
				return choice.material();
			}
		}

		return choices.get(choices.size() - 1).material();
	}

	private static Stack entry(String id, int min, int max) {
		return new Stack(id, min, max);
	}

	private static Choice choice(String material, int weight) {
		return new Choice(material, weight);
	}

	private static Pool pool(int minPicks, int maxPicks, Stack... options) {
		return new Pool(List.of(options), minPicks, maxPicks);
	}
}
