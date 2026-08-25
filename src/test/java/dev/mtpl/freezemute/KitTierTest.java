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
		assertEquals(EnchantPower.NONE, KitTier.POOR.enchantPower());
		assertEquals(EnchantPower.NONE, KitTier.COPPER.enchantPower());
		assertEquals(EnchantPower.WEAK, KitTier.IRON.enchantPower());
		assertEquals(EnchantPower.MIXED, KitTier.DIAMOND.enchantPower());
		assertEquals(EnchantPower.GOOD, KitTier.NETHERITE.enchantPower());
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
