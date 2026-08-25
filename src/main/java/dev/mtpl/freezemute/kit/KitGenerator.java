package dev.mtpl.freezemute.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import dev.mtpl.freezemute.kit.KitTier.Stack;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/**
 * Builds a kit that looks like it belonged to somebody who has been playing for a while:
 * worn gear, a few random enchantments on the better tiers, some food, some blocks and whatever
 * odds and ends were in their inventory.
 */
public final class KitGenerator {
	private KitGenerator() {
	}

	public static List<ItemStack> generate(MinecraftServer server, KitTier tier, Random random) {
		List<ItemStack> stacks = new ArrayList<>();
		int presence = presenceChance(tier);

		for (String id : tier.armour()) {
			if (random.nextInt(100) < presence) {
				gear(server, id, tier, random).ifPresent(stacks::add);
			}
		}

		for (String id : tier.tools()) {
			if (random.nextInt(100) < presence) {
				gear(server, id, tier, random).ifPresent(stacks::add);
			}
		}

		addSupplies(stacks, tier.food(), random);
		addSupplies(stacks, tier.blocks(), random);
		addSupplies(stacks, tier.oddsAndEnds(), random);

		return stacks;
	}

	/** How likely each piece of gear is to be there at all - poorer players have gaps. */
	private static int presenceChance(KitTier tier) {
		return switch (tier) {
			case POOR -> 65;
			case COPPER -> 75;
			case IRON -> 85;
			case IRON_DIAMOND -> 88;
			case DIAMOND -> 90;
			case DIAMOND_NETHERITE -> 95;
			case NETHERITE -> 100;
		};
	}

	private static Optional<ItemStack> gear(MinecraftServer server, String id, KitTier tier, Random random) {
		Optional<ItemStack> maybe = stack(id, 1);

		if (maybe.isEmpty()) {
			return maybe;
		}

		ItemStack stack = maybe.get();
		wear(stack, tier, random);
		KitEnchantments.apply(server, stack, id, tier.enchantPowerFor(id), random);
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

	private static void addSupplies(List<ItemStack> stacks, List<Stack> supplies, Random random) {
		for (Stack supply : supplies) {
			int spread = Math.max(1, supply.max() - supply.min() + 1);
			int count = supply.min() + random.nextInt(spread);

			if (count <= 0) {
				continue;
			}

			stack(supply.id(), count).ifPresent(stacks::add);
		}
	}

	/**
	 * Items are looked up by id rather than through the {@code Items} constants, which the
	 * 1.21.11 mappings do not name. An unknown id is skipped instead of handing out air.
	 */
	private static Optional<ItemStack> stack(String id, int count) {
		Optional<Item> item = Registries.ITEM.getOptionalValue(Identifier.ofVanilla(id));

		if (item.isEmpty()) {
			return Optional.empty();
		}

		ItemStack stack = new ItemStack(item.get(), count);
		return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
	}
}
