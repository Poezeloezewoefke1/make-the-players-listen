package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import dev.mtpl.freezemute.kit.KitTier;
import dev.mtpl.freezemute.kit.KitTier.EnchantPower;
import dev.mtpl.freezemute.kit.KitTier.Stack;

import org.junit.jupiter.api.Test;

class KitTierTest {
	@Test
	void everyTierIsAFullLoadout() {
		for (KitTier tier : KitTier.values()) {
			assertEquals(4, tier.armour().size(), tier + " should have a full set of armour");
			assertFalse(tier.tools().isEmpty(), tier + " should come with tools");
			assertFalse(tier.food().isEmpty(), tier + " should come with food");
			assertFalse(tier.blocks().isEmpty(), tier + " should come with blocks");
			assertFalse(tier.oddsAndEnds().isEmpty(), tier + " should come with odds and ends");
		}
	}

	@Test
	void onlyTheBetterTiersAreEnchanted() {
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantCeiling());
		assertEquals(EnchantPower.NONE, KitTier.COPPER.enchantCeiling());
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantCeiling());
		assertEquals(EnchantPower.MIXED, KitTier.IRON_DIAMOND.enchantCeiling());
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND.enchantCeiling());
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND_NETHERITE.enchantCeiling());
		assertEquals(EnchantPower.GOOD, KitTier.NETHERITE.enchantCeiling());
	}

	@Test
	void mixedKitsEnchantEachPieceForWhatItIsMadeOf() {
		// The iron half of an iron/diamond kit stays scrappy, the diamond half does not.
		assertEquals(EnchantPower.WEAK, KitTier.IRON_DIAMOND.enchantPowerFor("iron_helmet"));
		assertEquals(EnchantPower.MIXED, KitTier.IRON_DIAMOND.enchantPowerFor("diamond_sword"));

		// A full diamond kit gets the good rolls, which the half-iron one does not.
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND.enchantPowerFor("diamond_sword"));

		// Netherite is good too, so the top two tiers are about what you find rather than the rolls.
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND_NETHERITE.enchantPowerFor("diamond_pickaxe"));
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND_NETHERITE.enchantPowerFor("netherite_sword"));

		// A tier never lets a piece punch above its own ceiling.
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantPowerFor("diamond_sword"));
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantPowerFor("diamond_sword"));
		assertEquals(EnchantPower.NONE, KitTier.COPPER.enchantPowerFor("chainmail_chestplate"));

		// Leather and stone are never worth enchanting whatever the tier says.
		assertEquals(EnchantPower.NONE, KitTier.NETHERITE.enchantPowerFor("leather_boots"));
		assertEquals(EnchantPower.NONE, KitTier.DIAMOND.enchantPowerFor("stone_axe"));
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
		for (KitTier tier : KitTier.values()) {
			assertEquals(tier.name().toLowerCase(Locale.ROOT), tier.id());

			for (String id : tier.armour()) {
				assertValidId(id);
			}

			for (String id : tier.tools()) {
				assertValidId(id);
			}

			for (Stack stack : tier.food()) {
				assertValidStack(stack);
			}

			for (Stack stack : tier.blocks()) {
				assertValidStack(stack);
			}

			for (Stack stack : tier.oddsAndEnds()) {
				assertValidStack(stack);
			}
		}
	}

	private static void assertValidId(String id) {
		assertTrue(id.matches("[a-z0-9_]+"), id + " is not a plain item id");
	}

	private static void assertValidStack(Stack stack) {
		assertValidId(stack.id());
		assertTrue(stack.min() >= 0, stack.id() + " cannot have a negative minimum");
		assertTrue(stack.max() >= stack.min(), stack.id() + " has max below min");
		assertTrue(stack.max() <= 64, stack.id() + " asks for more than a stack");
	}
}
