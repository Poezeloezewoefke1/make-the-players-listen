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
	/** Whatever they scraped together: mismatched leather, copper and gold, all of it nearly gone. */
	POOR(60, 95, EnchantPower.NONE, EnchantRules.NORMAL,
			List.of(choice("leather", 5), choice("copper", 3), choice("golden", 2)),
			List.of(choice("stone", 4), choice("wooden", 4), choice("golden", 2))),

	COPPER(35, 80, EnchantPower.NONE, EnchantRules.NORMAL,
			List.of(choice("copper", 1)),
			List.of(choice("copper", 1))),

	IRON(25, 65, EnchantPower.WEAK, EnchantRules.NORMAL,
			List.of(choice("iron", 1)),
			List.of(choice("iron", 1))),

	/** Half kitted out: iron and diamond mixed, and which slots got the diamond is the roll. */
	CHUNGIE(20, 58, EnchantPower.MIXED, EnchantRules.NORMAL,
			List.of(choice("iron", 5), choice("diamond", 3)),
			List.of(choice("iron", 4), choice("diamond", 4))),

	DIAMOND(15, 50, EnchantPower.GOOD, EnchantRules.NORMAL,
			List.of(choice("diamond", 1)),
			List.of(choice("diamond", 1))),

	/**
	 * Diamond gear carrying the top enchantments it can hold, trimmed: Protection IV, Unbreaking
	 * III, Sharpness V, Efficiency V and the rest, but nothing that repairs itself and none of
	 * the movement extras. Named for exactly what it hands out.
	 */
	DIAMONDPROT4NOMENDINGUNBREAKING3(12, 45, EnchantPower.BEST,
			EnchantRules.ignoringMaterialCap("mending", "swift_sneak", "soul_speed"),
			List.of(choice("diamond", 1)),
			List.of(choice("diamond", 1))),

	/** Doing well for themselves: diamond and netherite mixed, again decided per slot. */
	RICH(10, 42, EnchantPower.BEST, EnchantRules.NORMAL,
			List.of(choice("diamond", 5), choice("netherite", 3)),
			List.of(choice("diamond", 4), choice("netherite", 4))),

	NETHERITE(5, 35, EnchantPower.BEST, EnchantRules.NORMAL,
			List.of(choice("netherite", 1)),
			List.of(choice("netherite", 1)));

	/** The armour slots every kit fills, in the order they are handed out. */
	public static final List<String> ARMOUR_SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

	/** The tools every kit comes with. */
	public static final List<String> TOOL_SLOTS = List.of("sword", "pickaxe", "axe", "shovel");

	/** The one item in a kit that has no material tier of its own. */
	public static final String SHIELD = "shield";

	/**
	 * The two ways a tier can bend the usual enchantment rules.
	 *
	 * @param materialDecides normally a piece can only carry what its material allows, so diamond
	 *     tops out below netherite however good the tier is. A tier that says false ignores that
	 *     and hands every piece the tier's own ceiling, which is how diamond gear ends up with
	 *     Protection IV
	 * @param banned enchantment ids this tier never hands out, as either a main or a side
	 */
	public record EnchantRules(boolean materialDecides, Set<String> banned) {
		/** What every ordinary tier uses: the material caps the piece and nothing is off limits. */
		public static final EnchantRules NORMAL = new EnchantRules(true, Set.of());

		/**
		 * Rules for a tier that hands every piece the tier's own ceiling whatever it is made of,
		 * and never rolls the named enchantments. Listing them at the call site means a tier
		 * named after its enchantments can be read off its own declaration.
		 */
		public static EnchantRules ignoringMaterialCap(String... banned) {
			return new EnchantRules(false, Set.of(banned));
		}

		public boolean allows(String enchantment) {
			return !banned.contains(enchantment);
		}
	}

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
	private final EnchantRules enchantRules;
	private final List<Choice> armourMaterials;
	private final List<Choice> toolMaterials;

	KitTier(int minWearPercent, int maxWearPercent, EnchantPower enchantPower, EnchantRules enchantRules,
			List<Choice> armourMaterials, List<Choice> toolMaterials) {
		this.minWearPercent = minWearPercent;
		this.maxWearPercent = maxWearPercent;
		this.enchantCeiling = enchantPower;
		this.enchantRules = enchantRules;
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

	/** True when this tier may hand out that enchantment at all. */
	public boolean allows(String enchantment) {
		return enchantRules.allows(enchantment);
	}

	/** The enchantments this tier never hands out. */
	public Set<String> bannedEnchantments() {
		return enchantRules.banned();
	}

	/** What this tier actually rolls for one item, once its material is taken into account. */
	public EnchantPower enchantPowerFor(String itemId) {
		EnchantPower material = EnchantPower.ofMaterial(itemId);

		// Leather, copper, stone, wood and gold are never enchanted, whatever the tier says.
		if (material == EnchantPower.NONE) {
			return EnchantPower.NONE;
		}

		return enchantRules.materialDecides() ? enchantCeiling.min(material) : enchantCeiling;
	}

	public List<Choice> armourMaterials() {
		return armourMaterials;
	}

	public List<Choice> toolMaterials() {
		return toolMaterials;
	}

	/** True when any slot can come out as more than one material. */
	public boolean isMixed() {
		return armourMaterials.size() > 1 || toolMaterials.size() > 1;
	}

	/**
	 * True when this tier is a deliberate blend of exactly two materials, which is a promise
	 * about what a kit contains rather than just a spread of odds. A tier drawing on three or
	 * more is a grab bag and is left entirely to chance.
	 */
	public boolean isBlend() {
		return armourMaterials.size() == 2 || toolMaterials.size() == 2;
	}

	/**
	 * Rolls the full set: four pieces of armour and four tools, every slot filled.
	 *
	 * <p>On a mixed tier each slot picks its own material, which is where the half-geared look
	 * comes from - one player ends up with the diamond chestplate, the next with diamond boots.
	 * The roll is then balanced so both of the tier's materials actually turn up, otherwise a
	 * rich kit could come out as plain diamond and be the tier below it wearing the wrong name.
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

	/**
	 * Promotes or demotes a slot until every material the tier offers is somewhere in the set.
	 * Only a two-material blend gets this: a grab bag of three or more is meant to be mismatched,
	 * so forcing one of each would make every kit the same shape.
	 */
	private static void balance(List<Gear> gear, boolean armour, List<Choice> materials, Random random) {
		if (materials.size() != 2) {
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
