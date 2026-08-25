package dev.mtpl.freezemute.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import dev.mtpl.freezemute.kit.KitTier.EnchantPower;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/**
 * Rolls the random enchantments for a kit.
 *
 * <p>Enchantments are looked up in the server's registry by id, so this keeps working with
 * data pack enchantments and does not depend on constants the mappings do not name.
 */
public final class KitEnchantments {
	/** One enchantment with the range of levels a tier may roll for it. */
	private record Option(String id, int minLevel, int maxLevel) {
	}

	private static final RegistryKey<Registry<Enchantment>> ENCHANTMENT_REGISTRY =
			RegistryKey.ofRegistry(Identifier.ofVanilla("enchantment"));

	private KitEnchantments() {
	}

	/**
	 * Enchants one piece of gear. How many enchantments it gets, and how strong, follows the
	 * tier: iron is scrappy, diamond is respectable, netherite is what you would expect from
	 * somebody who has been playing for months.
	 */
	public static void apply(MinecraftServer server, ItemStack stack, String itemId, EnchantPower power, Random random) {
		if (power == EnchantPower.NONE) {
			return;
		}

		List<Option> pool = pool(itemId, power);

		if (pool.isEmpty()) {
			return;
		}

		Registry<Enchantment> registry = registry(server);

		if (registry == null) {
			return;
		}

		List<Option> shuffled = new ArrayList<>(pool);
		java.util.Collections.shuffle(shuffled, random);

		int wanted = switch (power) {
			case WEAK -> 1 + random.nextInt(2);
			case MIXED -> 1 + random.nextInt(3);
			case GOOD -> 2 + random.nextInt(3);
			default -> 0;
		};

		int applied = 0;

		for (Option option : shuffled) {
			if (applied >= wanted) {
				break;
			}

			Optional<? extends RegistryEntry<Enchantment>> entry = registry.getEntry(Identifier.ofVanilla(option.id()));

			if (entry.isEmpty()) {
				continue;
			}

			int level = option.minLevel() + random.nextInt(Math.max(1, option.maxLevel() - option.minLevel() + 1));
			stack.addEnchantment(entry.get(), level);
			applied++;
		}
	}

	private static List<Option> pool(String itemId, EnchantPower power) {
		boolean sword = itemId.endsWith("_sword");
		boolean digger = itemId.endsWith("_pickaxe") || itemId.endsWith("_axe") || itemId.endsWith("_shovel");
		boolean boots = itemId.endsWith("_boots");
		boolean armour = boots || itemId.endsWith("_helmet") || itemId.endsWith("_chestplate")
				|| itemId.endsWith("_leggings");
		boolean bow = itemId.equals("bow") || itemId.equals("crossbow");

		List<Option> options = new ArrayList<>();

		switch (power) {
			case WEAK -> {
				if (sword) {
					options.add(new Option("sharpness", 1, 1));
					options.add(new Option("unbreaking", 1, 1));
				}

				if (digger) {
					options.add(new Option("efficiency", 1, 2));
					options.add(new Option("unbreaking", 1, 1));
				}

				if (armour) {
					options.add(new Option("protection", 1, 1));
					options.add(new Option("unbreaking", 1, 1));
				}

				if (boots) {
					options.add(new Option("feather_falling", 1, 1));
				}

				if (bow) {
					options.add(new Option("power", 1, 1));
				}
			}
			case MIXED -> {
				if (sword) {
					options.add(new Option("sharpness", 2, 3));
					options.add(new Option("looting", 1, 2));
					options.add(new Option("fire_aspect", 1, 1));
					options.add(new Option("unbreaking", 2, 2));
				}

				if (digger) {
					options.add(new Option("efficiency", 3, 4));
					options.add(new Option("unbreaking", 2, 3));
					options.add(new Option("fortune", 1, 2));
				}

				if (armour) {
					options.add(new Option("protection", 2, 3));
					options.add(new Option("unbreaking", 2, 3));
				}

				if (boots) {
					options.add(new Option("feather_falling", 2, 3));
					options.add(new Option("depth_strider", 1, 2));
				}

				if (bow) {
					options.add(new Option("power", 2, 3));
					options.add(new Option("punch", 1, 1));
				}
			}
			case GOOD -> {
				if (sword) {
					options.add(new Option("sharpness", 4, 5));
					options.add(new Option("looting", 2, 3));
					options.add(new Option("fire_aspect", 2, 2));
					options.add(new Option("unbreaking", 3, 3));
					options.add(new Option("mending", 1, 1));
					options.add(new Option("sweeping_edge", 2, 3));
				}

				if (digger) {
					options.add(new Option("efficiency", 4, 5));
					options.add(new Option("unbreaking", 3, 3));
					options.add(new Option("fortune", 2, 3));
					options.add(new Option("mending", 1, 1));
				}

				if (armour) {
					options.add(new Option("protection", 3, 4));
					options.add(new Option("unbreaking", 3, 3));
					options.add(new Option("mending", 1, 1));
					options.add(new Option("thorns", 1, 2));
				}

				if (boots) {
					options.add(new Option("feather_falling", 3, 4));
					options.add(new Option("depth_strider", 2, 3));
				}

				if (bow) {
					options.add(new Option("power", 4, 5));
					options.add(new Option("flame", 1, 1));
					options.add(new Option("unbreaking", 3, 3));
				}
			}
			default -> {
			}
		}

		return options;
	}

	@SuppressWarnings("unchecked")
	private static Registry<Enchantment> registry(MinecraftServer server) {
		try {
			return (Registry<Enchantment>) (Registry<?>) server.getRegistryManager().get(ENCHANTMENT_REGISTRY);
		} catch (RuntimeException exception) {
			return null;
		}
	}
}
