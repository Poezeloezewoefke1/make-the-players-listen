package dev.mtpl.freezemute.command;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import dev.mtpl.freezemute.kit.KitGenerator;
import dev.mtpl.freezemute.kit.KitTier;
import dev.mtpl.freezemute.util.Messages;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * {@code /kitgive <tier> [targets]} - hands out a used looking kit for a gear tier.
 *
 * <p>Each tier is a literal, so the tiers tab-complete and a typo is an unknown command rather
 * than a surprise kit.
 */
public final class KitCommand {
	private static final Random RANDOM = new Random();

	private KitCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("kitgive")
				.requires(source -> Permissions.check(source, Permissions.KITGIVE));

		for (KitTier tier : KitTier.values()) {
			root = root.then(tierNode(tier.id(), () -> tier));
		}

		// A tier per player, rolled on the spot, for when you want a whole group kitted out
		// without deciding who gets what.
		root = root.then(tierNode("random", () -> KitTier.values()[RANDOM.nextInt(KitTier.values().length)]));

		dispatcher.register(root);

		// The item registry is filled by the time commands are built, so this is the first safe
		// place to check that every id the kits use exists in this version.
		KitGenerator.logMissingIds();
	}

	/** One tier literal, usable on yourself or on whoever the targets argument picks out. */
	private static LiteralArgumentBuilder<ServerCommandSource> tierNode(String name, Supplier<KitTier> tier) {
		return CommandManager.literal(name)
				.executes(context -> give(context.getSource(), List.of(context.getSource().getPlayerOrThrow()), tier))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(context -> give(
								context.getSource(),
								EntityArgumentType.getPlayers(context, "targets"),
								tier)));
	}

	private static int give(ServerCommandSource source, Collection<ServerPlayerEntity> targets, Supplier<KitTier> roll) {
		MinecraftServer server = source.getServer();
		int count = 0;

		for (ServerPlayerEntity target : targets) {
			// Rolled per player on purpose: handing one command to a group should not give
			// everybody the same loadout.
			KitTier tier = roll.get();
			List<ItemStack> kit = KitGenerator.generate(server, tier, RANDOM);

			for (ItemStack stack : kit) {
				if (!target.giveItemStack(stack)) {
					// Inventory full: the rest lands at their feet rather than vanishing.
					target.dropItem(stack, false);
				}
			}

			String name = target.getGameProfile().name();
			int items = kit.size();
			source.sendFeedback(() -> Messages.success("Gave a " + tier.id() + " kit to " + name + " (" + items + " stacks)"), true);
			count++;
		}

		return count;
	}
}
