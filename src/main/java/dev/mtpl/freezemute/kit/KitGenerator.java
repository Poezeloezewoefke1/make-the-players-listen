package dev.mtpl.freezemute.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.kit.KitTier.Gear;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/**
 * Builds a kit: a full set of armour, a full set of tools and a shield, all of it worn the way
 * gear looks after a few weeks of use and enchanted at random on the tiers that get enchantments.
 *
 * <p>There is nothing else in a kit - no food, no blocks, no loot. Two kits of the same tier
 * differ in how battered each piece is, what it rolled for enchantments and at what level, and on
 * the mixed tiers which slots came out as the better material.
 */
public final class KitGenerator {
	private KitGenerator() {
	}

	public static List<ItemStack> generate(MinecraftServer server, KitTier tier, Random random) {
		List<ItemStack> stacks = new ArrayList<>();

		for (Gear gear : tier.rollGear(random)) {
			build(server, gear.itemId(), tier, random).ifPresent(stacks::add);
		}

		build(server, KitTier.SHIELD, tier, random).ifPresent(stacks::add);
		return stacks;
	}

	/**
	 * Checks that every id the kits can hand out actually exists in this version, and says so in
	 * the log. Item names move between Minecraft versions, and a missing id would otherwise show
	 * up as a kit quietly arriving a piece short.
	 */
	public static void logMissingIds() {
		List<String> missing = new ArrayList<>();

		for (KitTier tier : KitTier.values()) {
			for (String id : tier.everyPossibleItemId()) {
				if (Registries.ITEM.getOptionalValue(Identifier.ofVanilla(id)).isEmpty() && !missing.contains(id)) {
					missing.add(id);
				}
			}
		}

		if (missing.isEmpty()) {
			FreezeMute.LOGGER.info("Kit items: every id resolved");
		} else {
			FreezeMute.LOGGER.warn("Kit items missing from this version: {}", String.join(", ", missing));
		}
	}

	private static Optional<ItemStack> build(MinecraftServer server, String id, KitTier tier, Random random) {
		Optional<Item> item = Registries.ITEM.getOptionalValue(Identifier.ofVanilla(id));

		if (item.isEmpty()) {
			return Optional.empty();
		}

		ItemStack stack = new ItemStack(item.get(), 1);

		if (stack.isEmpty()) {
			return Optional.empty();
		}

		wear(stack, tier, random);
		KitEnchantments.apply(server, stack, id, tier, random);
		return Optional.of(stack);
	}

	/** Beats the item up a bit, the way real gear looks after a few weeks of use. */
	private static void wear(ItemStack stack, KitTier tier, Random random) {
		if (!stack.isDamageable()) {
			return;
		}

		int max = stack.getMaxDamage();

		if (max <= 1) {
			return;
		}

		int spread = Math.max(1, tier.maxWearPercent() - tier.minWearPercent() + 1);
		int percent = tier.minWearPercent() + random.nextInt(spread);
		int damage = Math.min(max - 1, (int) ((long) max * percent / 100L));

		if (damage > 0) {
			stack.setDamage(damage);
		}
	}
}
