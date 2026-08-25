package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import dev.mtpl.freezemute.kit.KitTier;
import dev.mtpl.freezemute.kit.KitTier.Choice;
import dev.mtpl.freezemute.kit.KitTier.EnchantPower;
import dev.mtpl.freezemute.kit.KitTier.Pool;
import dev.mtpl.freezemute.kit.KitTier.Stack;

import org.junit.jupiter.api.Test;

class KitTierTest {
	@Test
	void everyTierCanFillEverySlot() {
		for (KitTier tier : KitTier.values()) {
			assertFalse(tier.armourMaterials().isEmpty(), tier + " needs something to make armour out of");
			assertFalse(tier.toolMaterials().isEmpty(), tier + " needs something to make tools out of");
			assertFalse(tier.food().options().isEmpty(), tier + " should come with food");
			assertFalse(tier.blocks().options().isEmpty(), tier + " should come with blocks");
			assertFalse(tier.oddsAndEnds().options().isEmpty(), tier + " should come with odds and ends");
			assertTrue(tier.presencePercent() > 0 && tier.presencePercent() <= 100,
					tier + " needs a sane chance of a slot being filled");
		}
	}

	@Test
	void onlyTheBetterTiersAreEnchanted() {
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantCeiling());
		assertEquals(EnchantPower.NONE, KitTier.COPPER.enchantCeiling());
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantCeiling());
		assertEquals(EnchantPower.MIXED, KitTier.IRON_DIAMOND.enchantCeiling());
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND.enchantCeiling());
		assertEquals(EnchantPower.BEST, KitTier.DIAMOND_NETHERITE.enchantCeiling());
		assertEquals(EnchantPower.BEST, KitTier.NETHERITE.enchantCeiling());
	}

	@Test
	void mixedKitsEnchantEachPieceForWhatItIsMadeOf() {
		// The iron half of an iron/diamond kit stays scrappy, the diamond half does not.
		assertEquals(EnchantPower.WEAK, KitTier.IRON_DIAMOND.enchantPowerFor("iron_helmet"));
		assertEquals(EnchantPower.MIXED, KitTier.IRON_DIAMOND.enchantPowerFor("diamond_sword"));

		// A full diamond kit gets the good rolls, which the half-iron one does not.
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND.enchantPowerFor("diamond_sword"));

		// One tier up, the netherite pieces go past what diamond can reach.
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND_NETHERITE.enchantPowerFor("diamond_pickaxe"));
		assertEquals(EnchantPower.BEST, KitTier.DIAMOND_NETHERITE.enchantPowerFor("netherite_sword"));

		// A tier never lets a piece punch above its own ceiling.
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantPowerFor("diamond_sword"));
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantPowerFor("diamond_sword"));
		assertEquals(EnchantPower.NONE, KitTier.COPPER.enchantPowerFor("chainmail_chestplate"));

		// Leather, stone and gold are never worth enchanting whatever the tier says.
		assertEquals(EnchantPower.NONE, KitTier.NETHERITE.enchantPowerFor("leather_boots"));
		assertEquals(EnchantPower.NONE, KitTier.DIAMOND.enchantPowerFor("stone_axe"));
		assertEquals(EnchantPower.NONE, KitTier.NETHERITE.enchantPowerFor("golden_helmet"));

		// A bow has no material of its own, so it follows the kit it came in.
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantPowerFor("bow"));
		assertEquals(EnchantPower.BEST, KitTier.NETHERITE.enchantPowerFor("trident"));
	}

	@Test
	void cheaperGearIsMoreWornOut() {
		int previousMin = Integer.MAX_VALUE;

		for (KitTier tier : KitTier.values()) {
			assertTrue(tier.minWearPercent() >= 0 && tier.maxWearPercent() < 100,
					tier + " wear has to stay inside 0-99 percent so nothing arrives broken");
			assertTrue(tier.minWearPercent() < tier.maxWearPercent(), tier + " needs a wear range to roll in");
			assertTrue(tier.minWearPercent() < previousMin, tier + " should be less battered than the tier below it");
			previousMin = tier.minWearPercent();
		}
	}

	@Test
	void itemIdsLookLikeItemIds() {
		Random random = new Random(1);

		for (KitTier tier : KitTier.values()) {
			assertEquals(tier.name().toLowerCase(Locale.ROOT), tier.id());

			for (Choice choice : tier.armourMaterials()) {
				assertValidId(choice.material());
				assertTrue(choice.weight() > 0, choice.material() + " needs a positive weight to ever be picked");
			}

			for (Choice choice : tier.toolMaterials()) {
				assertValidId(choice.material());
				assertTrue(choice.weight() > 0, choice.material() + " needs a positive weight to ever be picked");
			}

			for (Pool pool : List.of(tier.food(), tier.blocks(), tier.oddsAndEnds())) {
				assertValidPool(tier, pool);
			}

			// The rolled ids have to look like real items too, not just the parts they are made of.
			for (int roll = 0; roll < 200; roll++) {
				for (String slot : KitTier.ARMOUR_SLOTS) {
					assertValidId(tier.rollArmour(slot, random));
				}

				for (String slot : KitTier.TOOL_SLOTS) {
					assertValidId(tier.rollTool(slot, random));
				}
			}
		}
	}

	@Test
	void armourAndToolsAreMadeOfMaterialsThatExist() {
		// Chainmail has no tools and stone has no armour, so a tier must never offer a material
		// for a slot the game cannot make out of it.
		Set<String> armourMaterials = Set.of("leather", "chainmail", "golden", "iron", "diamond", "netherite");
		Set<String> toolMaterials = Set.of("wooden", "stone", "golden", "iron", "diamond", "netherite");

		for (KitTier tier : KitTier.values()) {
			for (Choice choice : tier.armourMaterials()) {
				assertTrue(armourMaterials.contains(choice.material()),
						tier + " offers " + choice.material() + " armour, which does not exist");
			}

			for (Choice choice : tier.toolMaterials()) {
				assertTrue(toolMaterials.contains(choice.material()),
						tier + " offers " + choice.material() + " tools, which do not exist");
			}
		}
	}

	@Test
	void twoKitsOfTheSameTierComeOutDifferent() {
		// The whole point of the rolls: giving one tier to a group should not hand everybody the
		// same loadout. Compare a batch of rolls rather than two, so this cannot pass by luck.
		for (KitTier tier : KitTier.values()) {
			Random random = new Random(20250825L + tier.ordinal());
			Set<String> seen = new HashSet<>();

			for (int roll = 0; roll < 30; roll++) {
				seen.add(describe(tier, random));
			}

			assertTrue(seen.size() > 20,
					tier + " handed out " + seen.size() + " different kits in 30 rolls, which is too samey");
		}
	}

	@Test
	void aMixedTierAlwaysShowsWhatItIsNamedAfter() {
		// Every slot rolls on its own, so a diamond_netherite kit can come up all diamond, which
		// is the tier below it wearing the wrong name. One piece gets promoted when that happens.
		Random random = new Random(11);

		for (KitTier tier : KitTier.values()) {
			if (tier.signature() == null) {
				continue;
			}

			for (int roll = 0; roll < 500; roll++) {
				List<String> gear = new ArrayList<>();

				for (String slot : KitTier.ARMOUR_SLOTS) {
					gear.add(tier.rollArmour(slot, random));
				}

				for (String slot : KitTier.TOOL_SLOTS) {
					gear.add(tier.rollTool(slot, random));
				}

				tier.ensureSignature(gear, random);

				assertTrue(gear.stream().anyMatch(id -> id.startsWith(tier.signature() + "_")),
						tier + " handed out a kit with no " + tier.signature() + " in it: " + gear);
			}
		}
	}

	@Test
	void promotingAPieceKeepsItInTheSameSlot() {
		List<String> gear = new ArrayList<>(List.of("iron_helmet", "iron_chestplate", "iron_sword"));
		KitTier.IRON_DIAMOND.ensureSignature(gear, new Random(5));

		assertEquals(3, gear.size(), "promoting a piece should not add or drop one");
		assertEquals(1, gear.stream().filter(id -> id.startsWith("diamond_")).count(),
				"exactly one piece should have been promoted");

		for (String id : gear) {
			String slot = id.substring(id.indexOf('_') + 1);
			assertTrue(List.of("helmet", "chestplate", "sword").contains(slot), "slot changed to " + slot);
		}
	}

	@Test
	void aTierWithOneMaterialIsLeftAlone() {
		List<String> gear = new ArrayList<>(List.of("diamond_helmet", "diamond_sword"));
		KitTier.DIAMOND.ensureSignature(gear, new Random(5));

		assertEquals(List.of("diamond_helmet", "diamond_sword"), gear);
	}

	@Test
	void everySupplyPoolHoldsBackSomething() {
		// A pool that always hands out everything in it is just a fixed list with extra steps.
		for (KitTier tier : KitTier.values()) {
			for (Pool pool : List.of(tier.food(), tier.blocks(), tier.oddsAndEnds())) {
				assertTrue(pool.maxPicks() < pool.options().size(),
						tier + " has a pool that always empties itself, so kits would all match");
			}
		}
	}

	@Test
	void weightedPicksFavourTheHeavierChoice() {
		List<Choice> choices = List.of(new Choice("common", 9), new Choice("rare", 1));
		Random random = new Random(7);
		int common = 0;

		for (int roll = 0; roll < 1000; roll++) {
			if (KitTier.weighted(choices, random).equals("common")) {
				common++;
			}
		}

		assertTrue(common > 800 && common < 980, "expected roughly nine in ten to be common, got " + common);
	}

	@Test
	void pickedSuppliesAreNeverRepeatedInOneKit() {
		Random random = new Random(3);

		for (KitTier tier : KitTier.values()) {
			for (int roll = 0; roll < 100; roll++) {
				List<Stack> supplies = tier.rollSupplies(random);
				Set<Stack> unique = new HashSet<>(supplies);
				assertEquals(supplies.size(), unique.size(), tier + " handed out the same entry twice");
			}
		}
	}

	/** A rolled kit written out as text, so two rolls can be compared. */
	private static String describe(KitTier tier, Random random) {
		List<String> parts = new ArrayList<>();

		for (String slot : KitTier.ARMOUR_SLOTS) {
			parts.add(tier.rollArmour(slot, random));
		}

		for (String slot : KitTier.TOOL_SLOTS) {
			parts.add(tier.rollTool(slot, random));
		}

		for (Stack stack : tier.rollSupplies(random)) {
			parts.add(stack.id());
		}

		return String.join(",", parts);
	}

	private static void assertValidPool(KitTier tier, Pool pool) {
		assertTrue(pool.minPicks() >= 0, tier + " cannot pick a negative number of things");
		assertTrue(pool.maxPicks() >= pool.minPicks(), tier + " has a pool with max picks below min");

		for (Stack stack : pool.options()) {
			assertValidStack(stack);
		}
	}

	private static void assertValidId(String id) {
		assertTrue(id.matches("[a-z0-9_]+"), id + " is not a plain item id");
		assertNotEquals("", id);
	}

	private static void assertValidStack(Stack stack) {
		assertValidId(stack.id());
		assertTrue(stack.min() >= 1, stack.id() + " should hand out at least one, the pool decides if it appears");
		assertTrue(stack.max() >= stack.min(), stack.id() + " has max below min");
		assertTrue(stack.max() <= 64, stack.id() + " asks for more than a stack");
	}
}
