package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import dev.mtpl.freezemute.kit.KitTier;
import dev.mtpl.freezemute.kit.KitTier.Choice;
import dev.mtpl.freezemute.kit.KitTier.EnchantPower;
import dev.mtpl.freezemute.kit.KitTier.Gear;

import org.junit.jupiter.api.Test;

class KitTierTest {
	@Test
	void everyKitIsAFullSet() {
		Random random = new Random(1);

		for (KitTier tier : KitTier.values()) {
			for (int roll = 0; roll < 200; roll++) {
				List<Gear> gear = tier.rollGear(random);

				assertEquals(8, gear.size(), tier + " should hand out four pieces of armour and four tools");

				Set<String> slots = new HashSet<>();

				for (Gear piece : gear) {
					assertTrue(slots.add(piece.slot()), tier + " filled " + piece.slot() + " twice");
				}

				for (String slot : KitTier.ARMOUR_SLOTS) {
					assertTrue(slots.contains(slot), tier + " left the " + slot + " slot empty");
				}

				for (String slot : KitTier.TOOL_SLOTS) {
					assertTrue(slots.contains(slot), tier + " came without a " + slot);
				}
			}
		}
	}

	@Test
	void straightTiersNeverMixInAnotherMaterial() {
		// "Give me the netherite kit" should not quietly hand over diamond pieces.
		Random random = new Random(2);

		for (KitTier tier : KitTier.values()) {
			if (tier.isMixed()) {
				continue;
			}

			assertEquals(1, tier.armourMaterials().size(), tier + " should be one material of armour");
			assertEquals(1, tier.toolMaterials().size(), tier + " should be one material of tools");

			String armour = tier.armourMaterials().get(0).material();
			String tools = tier.toolMaterials().get(0).material();

			for (int roll = 0; roll < 200; roll++) {
				for (Gear piece : tier.rollGear(random)) {
					String expected = piece.armour() ? armour : tools;
					assertEquals(expected, piece.material(),
							tier + " handed out a " + piece.itemId() + ", which is not " + expected);
				}
			}
		}
	}

	@Test
	void blendedTiersAlwaysShowBothMaterials() {
		// Otherwise rich can come out as plain diamond, which is the tier below it wearing the
		// wrong name - or as full netherite, which is the tier above.
		Random random = new Random(3);

		for (KitTier tier : KitTier.values()) {
			if (!tier.isBlend()) {
				continue;
			}

			for (int roll = 0; roll < 500; roll++) {
				List<Gear> gear = tier.rollGear(random);

				for (Choice choice : tier.armourMaterials()) {
					assertTrue(gear.stream().anyMatch(piece -> piece.armour()
									&& piece.material().equals(choice.material())),
							tier + " handed out armour with no " + choice.material() + " in it");
				}

				for (Choice choice : tier.toolMaterials()) {
					assertTrue(gear.stream().anyMatch(piece -> !piece.armour()
									&& piece.material().equals(choice.material())),
							tier + " handed out tools with no " + choice.material() + " in them");
				}
			}
		}
	}

	@Test
	void mixedTiersVaryWhichSlotsGotWhichMaterial() {
		Random random = new Random(4);

		for (KitTier tier : KitTier.values()) {
			if (!tier.isMixed()) {
				continue;
			}

			Set<String> seen = new HashSet<>();

			for (int roll = 0; roll < 40; roll++) {
				StringBuilder shape = new StringBuilder();

				for (Gear piece : tier.rollGear(random)) {
					shape.append(piece.itemId()).append(' ');
				}

				seen.add(shape.toString());
			}

			assertTrue(seen.size() > 25,
					tier + " only produced " + seen.size() + " layouts in 40 rolls, which is too samey");
		}
	}

	@Test
	void onlyTheBetterTiersAreEnchanted() {
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantCeiling());
		assertEquals(EnchantPower.NONE, KitTier.COPPER.enchantCeiling());
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantCeiling());
		assertEquals(EnchantPower.MIXED, KitTier.CHUNGIE.enchantCeiling());
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND.enchantCeiling());
		assertEquals(EnchantPower.BEST, KitTier.RICH.enchantCeiling());
		assertEquals(EnchantPower.BEST, KitTier.NETHERITE.enchantCeiling());
	}

	@Test
	void mixedKitsEnchantEachPieceForWhatItIsMadeOf() {
		// The iron half of an iron/diamond kit stays scrappy, the diamond half does not.
		assertEquals(EnchantPower.WEAK, KitTier.CHUNGIE.enchantPowerFor("iron_helmet"));
		assertEquals(EnchantPower.MIXED, KitTier.CHUNGIE.enchantPowerFor("diamond_sword"));

		// A full diamond kit gets the good rolls, which the half-iron one does not.
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND.enchantPowerFor("diamond_sword"));

		// One tier up, the netherite pieces go past what diamond can reach.
		assertEquals(EnchantPower.GOOD, KitTier.RICH.enchantPowerFor("diamond_pickaxe"));
		assertEquals(EnchantPower.BEST, KitTier.RICH.enchantPowerFor("netherite_sword"));

		// A tier never lets a piece punch above its own ceiling.
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantPowerFor("diamond_sword"));
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantPowerFor("diamond_sword"));

		// Copper, leather, stone and gold are never worth enchanting whatever the tier says.
		assertEquals(EnchantPower.NONE, KitTier.COPPER.enchantPowerFor("copper_chestplate"));
		assertEquals(EnchantPower.NONE, KitTier.NETHERITE.enchantPowerFor("leather_boots"));
		assertEquals(EnchantPower.NONE, KitTier.DIAMOND.enchantPowerFor("stone_axe"));
		assertEquals(EnchantPower.NONE, KitTier.NETHERITE.enchantPowerFor("golden_helmet"));

		// The shield has no material of its own, so it follows the kit it came in.
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantPowerFor(KitTier.SHIELD));
		assertEquals(EnchantPower.BEST, KitTier.NETHERITE.enchantPowerFor(KitTier.SHIELD));
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantPowerFor(KitTier.SHIELD));
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
	void everyKitIsGearAndNothingElse() {
		// The whole item list is armour, tools and a shield - no food, blocks or loot can sneak in.
		for (KitTier tier : KitTier.values()) {
			for (String id : tier.everyPossibleItemId()) {
				boolean gear = KitTier.ARMOUR_SLOTS.stream().anyMatch(slot -> id.endsWith("_" + slot))
						|| KitTier.TOOL_SLOTS.stream().anyMatch(slot -> id.endsWith("_" + slot));

				assertTrue(gear || id.equals(KitTier.SHIELD), tier + " could hand out " + id + ", which is not gear");
			}
		}
	}

	@Test
	void everyKitComesWithAShield() {
		for (KitTier tier : KitTier.values()) {
			assertTrue(tier.everyPossibleItemId().contains(KitTier.SHIELD), tier + " should include a shield");
		}
	}

	@Test
	void materialsAndIdsLookLikeItemIds() {
		Set<String> armourMaterials = Set.of("leather", "copper", "chainmail", "golden", "iron", "diamond", "netherite");
		Set<String> toolMaterials = Set.of("wooden", "stone", "copper", "golden", "iron", "diamond", "netherite");

		for (KitTier tier : KitTier.values()) {
			assertEquals(tier.name().toLowerCase(Locale.ROOT), tier.id());
			assertFalse(tier.armourMaterials().isEmpty(), tier + " needs something to make armour out of");
			assertFalse(tier.toolMaterials().isEmpty(), tier + " needs something to make tools out of");

			for (Choice choice : tier.armourMaterials()) {
				assertTrue(choice.weight() > 0, choice.material() + " needs a positive weight to ever be picked");
				assertTrue(armourMaterials.contains(choice.material()),
						tier + " offers " + choice.material() + " armour, which does not exist");
			}

			for (Choice choice : tier.toolMaterials()) {
				assertTrue(choice.weight() > 0, choice.material() + " needs a positive weight to ever be picked");
				assertTrue(toolMaterials.contains(choice.material()),
						tier + " offers " + choice.material() + " tools, which do not exist");
			}

			for (String id : tier.everyPossibleItemId()) {
				assertTrue(id.matches("[a-z0-9_]+"), id + " is not a plain item id");
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
}
