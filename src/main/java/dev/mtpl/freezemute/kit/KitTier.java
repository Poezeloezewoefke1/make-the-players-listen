package dev.mtpl.freezemute.kit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The gear tiers {@code /kitgive} can hand out.
 *
 * <p>A kit is gear and nothing else: a full set of armour, a full set of tools and a shield. No
 * food, no blocks, no loot. What varies between two kits of the same tier is how worn each piece
 * is, what it is enchanted with and at what level, and - on the mixed tiers - which slots came
 * out as the better material.
 *
 * <p>Everything is written as an item id rather than an {@code Items} constant on purpose: the
 * mappings for 1.21.11 do not name the tool, armour and enchantment constants, and ids are also
 * what a server owner would recognise.
 */
public enum KitTier {
	POOR(45, 90, EnchantPower.NONE,
			List.of(choice("leather", 1)),
			List.of(choice("stone", 1))),

	COPPER(35, 80, EnchantPower.NONE,
			List.of(choice("copper", 1)),
			List.of(choice("copper", 1))),

	IRON(25, 65, EnchantPower.WEAK,
			List.of(choice("iron", 1)),
			List.of(choice("iron", 1))),

	/** Half kitted out: iron and diamond mixed, and which slots got the diamond is the roll. */
	IRON_DIAMOND(20, 58, EnchantPower.MIXED,
			List.of(choice("iron", 5), choice("diamond", 3)),
			List.of(choice("iron", 4), choice("diamond", 4))),

	DIAMOND(15, 50, EnchantPower.GOOD,
			List.of(choice("diamond", 1)),
			List.of(choice("diamond", 1))),

	/** Most of the way there: diamond and netherite mixed, again decided per slot. */
	DIAMOND_NETHERITE(10, 42, EnchantPower.BEST,
			List.of(choice("diamond", 5), choice("netherite", 3)),
			List.of(choice("diamond", 4), choice("netherite", 4))),

	NETHERITE(5, 35, EnchantPower.BEST,
			List.of(choice("netherite", 1)),
			List.of(choice("netherite", 1)));

	/** The armour slots every kit fills, in the order they are handed out. */
	public static final List<String> ARMOUR_SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

	/** The tools every kit comes with. */
	public static final List<String> TOOL_SLOTS = List.of("sword", "pickaxe", "axe", "shovel");

	/** The one item in a kit that has no material tier of its own. */
	public static final String SHIELD = "shield";

	/** One material a slot may come out as, and how often it should win the roll. */
	public record Choice(String material, int weight) {
	}

	/** One rolled piece of gear: which slot, whether it is armour, and what it is made of. */
	public record Gear(String slot, boolean armour, String material) {
		public String itemId() {
			return material + "_" + slot;
		}

		/** The same slot in a different material. */
		public Gear as(String other) {
			return new Gear(slot, armour, other);
		}
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

			if (itemId.startsWith("copper_") || itemId.startsWith("stone_") || itemId.startsWith("wooden_")
					|| itemId.startsWith("leather_") || itemId.startsWith("golden_")) {
				return NONE;
			}

			// The shield has no material of its own, so the kit's ceiling decides for it.
			return BEST;
		}
	}

	private final int minWearPercent;
	private final int maxWearPercent;
	private final EnchantPower enchantCeiling;
	private final List<Choice> armourMaterials;
	private final List<Choice> toolMaterials;

	KitTier(int minWearPercent, int maxWearPercent, EnchantPower enchantPower,
			List<Choice> armourMaterials, List<Choice> toolMaterials) {
		this.minWearPercent = minWearPercent;
		this.maxWearPercent = maxWearPercent;
		this.enchantCeiling = enchantPower;
		this.armourMaterials = armourMaterials;
		this.toolMaterials = toolMaterials;
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

	public List<Choice> armourMaterials() {
		return armourMaterials;
	}

	public List<Choice> toolMaterials() {
		return toolMaterials;
	}

	/** True when this tier is a blend of two materials rather than one straight set. */
	public boolean isMixed() {
		return armourMaterials.size() > 1 || toolMaterials.size() > 1;
	}

	/**
	 * Rolls the full set: four pieces of armour and four tools, every slot filled.
	 *
	 * <p>On a mixed tier each slot picks its own material, which is where the half-geared look
	 * comes from - one player ends up with the diamond chestplate, the next with diamond boots.
	 * The roll is then balanced so both of the tier's materials actually turn up, otherwise a
	 * diamond_netherite kit could come out as plain diamond and be the tier below it wearing the
	 * wrong name.
	 */
	public List<Gear> rollGear(Random random) {
		List<Gear> gear = new ArrayList<>();

		for (String slot : ARMOUR_SLOTS) {
			gear.add(new Gear(slot, true, weighted(armourMaterials, random)));
		}

		for (String slot : TOOL_SLOTS) {
			gear.add(new Gear(slot, false, weighted(toolMaterials, random)));
		}

		balance(gear, true, armourMaterials, random);
		balance(gear, false, toolMaterials, random);
		return gear;
	}

	/** Promotes or demotes a slot until every material the tier offers is somewhere in the set. */
	private static void balance(List<Gear> gear, boolean armour, List<Choice> materials, Random random) {
		if (materials.size() < 2) {
			return;
		}

		List<Integer> slots = new ArrayList<>();

		for (int index = 0; index < gear.size(); index++) {
			if (gear.get(index).armour() == armour) {
				slots.add(index);
			}
		}

		if (slots.size() < materials.size()) {
			return;
		}

		Set<String> missing = new LinkedHashSet<>();

		for (Choice choice : materials) {
			missing.add(choice.material());
		}

		for (int index : slots) {
			missing.remove(gear.get(index).material());
		}

		for (String material : missing) {
			// Pick a slot that is not the only one holding a material nothing else has.
			for (int attempt = 0; attempt < slots.size(); attempt++) {
				int index = slots.get(random.nextInt(slots.size()));
				String current = gear.get(index).material();
				long others = slots.stream().filter(other -> gear.get(other).material().equals(current)).count();

				if (others > 1) {
					gear.set(index, gear.get(index).as(material));
					break;
				}
			}
		}
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

	/** Every item id this tier could ever hand out, for the startup check. */
	public List<String> everyPossibleItemId() {
		List<String> ids = new ArrayList<>();

		for (Choice choice : armourMaterials) {
			for (String slot : ARMOUR_SLOTS) {
				ids.add(choice.material() + "_" + slot);
			}
		}

		for (Choice choice : toolMaterials) {
			for (String slot : TOOL_SLOTS) {
				ids.add(choice.material() + "_" + slot);
			}
		}

		ids.add(SHIELD);
		return ids;
	}

	private static Choice choice(String material, int weight) {
		return new Choice(material, weight);
	}
}
