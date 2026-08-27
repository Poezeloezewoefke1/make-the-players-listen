package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.mtpl.freezemute.kit.KitEnchantments;
import dev.mtpl.freezemute.kit.KitTier;
import dev.mtpl.freezemute.kit.KitTier.EnchantPower;
import dev.mtpl.freezemute.kit.KitTier.Gear;

import org.junit.jupiter.api.Test;

class KitEnchantmentsTest {
	private static final List<EnchantPower> ENCHANTED =
			List.of(EnchantPower.WEAK, EnchantPower.MIXED, EnchantPower.GOOD, EnchantPower.BEST);

	@Test
	void everyPieceOfGearHasAMainEnchantment() {
		// An enchanted chestplate always actually protects, an enchanted sword always actually
		// hits harder - the sides go on top of that rather than instead of it.
		assertEquals("protection", KitEnchantments.primaryFor("diamond_helmet"));
		assertEquals("protection", KitEnchantments.primaryFor("netherite_chestplate"));
		assertEquals("protection", KitEnchantments.primaryFor("iron_leggings"));
		assertEquals("protection", KitEnchantments.primaryFor("diamond_boots"));
		assertEquals("sharpness", KitEnchantments.primaryFor("netherite_sword"));
		assertEquals("sharpness", KitEnchantments.primaryFor("diamond_axe"));
		assertEquals("efficiency", KitEnchantments.primaryFor("iron_pickaxe"));
		assertEquals("efficiency", KitEnchantments.primaryFor("diamond_shovel"));
		assertEquals("unbreaking", KitEnchantments.primaryFor(KitTier.SHIELD));

		Random random = new Random(1);

		for (KitTier tier : KitTier.values()) {
			for (Gear piece : tier.rollGear(random)) {
				assertNotNull(KitEnchantments.primaryFor(piece.itemId()),
						piece.itemId() + " has no main enchantment to build on");
			}
		}
	}

	@Test
	void nothingThatIsNotGearGetsAMainEnchantment() {
		assertNull(KitEnchantments.primaryFor("cobblestone"));
		assertNull(KitEnchantments.primaryFor("golden_apple"));
	}

	@Test
	void thornsIsNeverHandedOut() {
		for (String itemId : everyKitItemId()) {
			for (EnchantPower power : ENCHANTED) {
				assertFalse(KitEnchantments.sideIds(itemId, power).contains("thorns"),
						itemId + " could roll Thorns at " + power);
				assertFalse("thorns".equals(KitEnchantments.primaryFor(itemId)));
			}
		}
	}

	@Test
	void onlyPlainProtectionAndPlainSharpnessAreUsed() {
		List<String> banned = List.of("blast_protection", "fire_protection", "projectile_protection",
				"smite", "bane_of_arthropods");

		for (String itemId : everyKitItemId()) {
			for (EnchantPower power : ENCHANTED) {
				for (String id : KitEnchantments.sideIds(itemId, power)) {
					assertFalse(banned.contains(id), itemId + " could roll " + id + " at " + power);
				}
			}
		}
	}

	@Test
	void betterTiersDrawOnWiderPools() {
		// A helmet is the clearest case: nothing extra at weak, Aqua Affinity and Respiration
		// once the kit is worth enchanting properly.
		List<String> weak = KitEnchantments.sideIds("iron_helmet", EnchantPower.WEAK);
		List<String> good = KitEnchantments.sideIds("diamond_helmet", EnchantPower.GOOD);
		List<String> best = KitEnchantments.sideIds("netherite_helmet", EnchantPower.BEST);

		assertFalse(weak.contains("aqua_affinity"), "an iron helmet should not roll Aqua Affinity");
		assertTrue(good.contains("aqua_affinity"), "a diamond helmet should be able to roll Aqua Affinity");
		assertTrue(good.contains("respiration"));
		assertTrue(good.contains("mending"));
		assertTrue(best.size() >= good.size(), "netherite should draw on at least as much as diamond");

		// The pieces that only the top tier ever sees.
		assertTrue(KitEnchantments.sideIds("netherite_boots", EnchantPower.BEST).contains("soul_speed"));
		assertTrue(KitEnchantments.sideIds("netherite_leggings", EnchantPower.BEST).contains("swift_sneak"));
		assertFalse(KitEnchantments.sideIds("diamond_leggings", EnchantPower.GOOD).contains("swift_sneak"));
	}

	@Test
	void conflictingSidesArePreferenceOrderedForTheTopTier() {
		// The best kit takes its pool in order rather than shuffling, so the first of two
		// conflicting options is the one that lands. Fortune should beat Silk Touch, and Depth
		// Strider should beat Frost Walker.
		List<String> pickaxe = KitEnchantments.sideIds("netherite_pickaxe", EnchantPower.BEST);
		assertTrue(pickaxe.indexOf("fortune") < pickaxe.indexOf("silk_touch"));

		List<String> boots = KitEnchantments.sideIds("netherite_boots", EnchantPower.BEST);
		assertTrue(boots.indexOf("depth_strider") < boots.indexOf("frost_walker"));
	}

	@Test
	void theMainEnchantmentIsNeverAlsoASide() {
		// The shield is the case that catches this: Unbreaking is its main enchantment and also
		// sits in the shared durability pool, so it would otherwise be applied twice.
		for (String itemId : everyKitItemId()) {
			String main = KitEnchantments.primaryFor(itemId);

			for (EnchantPower power : ENCHANTED) {
				assertFalse(KitEnchantments.sideIds(itemId, power).contains(main),
						itemId + " lists its main enchantment " + main + " as a side at " + power);
			}
		}

		assertTrue(KitEnchantments.sideIds(KitTier.SHIELD, EnchantPower.BEST).contains("mending"),
				"a shield should still be able to roll Mending as a side");
	}

	@Test
	void theFixedDiamondTierGetsNetheriteGradeEnchantsWithoutMending() {
		KitTier tier = KitTier.DIAMONDPROT4NOMENDINGUNBREAKING3;

		// Diamond normally tops out below netherite, however good the tier is. This one ignores
		// that, which is the whole point of it - Protection IV on diamond armour.
		assertEquals(EnchantPower.BEST, tier.enchantPowerFor("diamond_helmet"));
		assertEquals(EnchantPower.BEST, tier.enchantPowerFor("diamond_sword"));
		assertEquals(EnchantPower.BEST, tier.enchantPowerFor("diamond_pickaxe"));
		assertEquals(EnchantPower.BEST, tier.enchantPowerFor(KitTier.SHIELD));

		assertFalse(tier.allowsMending(), "the name says no mending");

		for (String itemId : everyKitItemId()) {
			assertFalse(KitEnchantments.sideIds(itemId, EnchantPower.BEST, false).contains("mending"),
					itemId + " could still roll Mending on the no-mending tier");
		}

		// Everything else the top tier gives is still there.
		List<String> helmet = KitEnchantments.sideIds("diamond_helmet", EnchantPower.BEST, false);
		assertTrue(helmet.contains("unbreaking"));
		assertTrue(helmet.contains("aqua_affinity"));
		assertTrue(helmet.contains("respiration"));
	}

	@Test
	void theOrdinaryDiamondTierIsUnchanged() {
		// The new tier must not have moved the normal one: diamond still caps at good, and
		// still gets Mending.
		assertEquals(EnchantPower.GOOD, KitTier.DIAMOND.enchantPowerFor("diamond_helmet"));
		assertTrue(KitTier.DIAMOND.allowsMending());
		assertTrue(KitEnchantments.sideIds("diamond_helmet", EnchantPower.GOOD).contains("mending"));
	}

	@Test
	void unenchantedKitsHaveNoSidesEither() {
		for (String itemId : everyKitItemId()) {
			assertTrue(KitEnchantments.sideIds(itemId, EnchantPower.NONE).isEmpty(),
					itemId + " offered sides on a kit that is not enchanted at all");
		}
	}

	private static List<String> everyKitItemId() {
		List<String> ids = new ArrayList<>();

		for (KitTier tier : KitTier.values()) {
			for (String id : tier.everyPossibleItemId()) {
				if (!ids.contains(id)) {
					ids.add(id);
				}
			}
		}

		return ids;
	}
}
