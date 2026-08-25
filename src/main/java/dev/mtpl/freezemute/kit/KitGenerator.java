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
 *
 * <p>Every part of it is rolled fresh, so handing the same tier to a group of players gives each
 * of them a different loadout rather than the same one several times over: which material each
 * slot came out as, whether the slot is filled at all, how worn it is, what it is enchanted with
 * and which supplies came along are all separate rolls.
 */
public final class KitGenerator {
	private KitGenerator() {
	}

	public static List<ItemStack> generate(MinecraftServer server, KitTier tier, Random random) {
		List<String> gearIds = new ArrayList<>();

		for (String slot : KitTier.ARMOUR_SLOTS) {
			if (random.nextInt(100) < tier.presencePercent()) {
				gearIds.add(tier.rollArmour(slot, random));
			}
		}

		for (String slot : KitTier.TOOL_SLOTS) {
			if (random.nextInt(100) < tier.presencePercent()) {
				gearIds.add(tier.rollTool(slot, random));
			}
		}

		tier.ensureSignature(gearIds, random);

		List<ItemStack> stacks = new ArrayList<>();

		for (String id : gearIds) {
			gear(server, id, tier, random).ifPresent(stacks::add);
		}

		for (Stack supply : tier.rollSupplies(random)) {
			supply(server, supply, tier, random).ifPresent(stacks::add);
		}

		return stacks;
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

	/**
	 * Rolls one supply stack. Anything with a durability bar - a bow, a shield, a trident - is
	 * treated as gear too, so it comes out used and possibly enchanted rather than brand new.
	 */
	private static Optional<ItemStack> supply(MinecraftServer server, Stack supply, KitTier tier, Random random) {
		int spread = Math.max(1, supply.max() - supply.min() + 1);
		int count = Math.max(1, supply.min() + random.nextInt(spread));
		Optional<ItemStack> maybe = stack(supply.id(), count);

		if (maybe.isEmpty()) {
			return maybe;
		}

		ItemStack stack = maybe.get();

		if (stack.isDamageable()) {
			wear(stack, tier, random);
			KitEnchantments.apply(server, stack, supply.id(), tier.enchantPowerFor(supply.id()), random);
		}

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
