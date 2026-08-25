package dev.mtpl.freezemute.command;

import java.util.Collection;
import java.util.List;
import java.util.Random;

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
			root = root.then(CommandManager.literal(tier.id())
					.executes(context -> give(context.getSource(), List.of(context.getSource().getPlayerOrThrow()), tier))
					.then(CommandManager.argument("targets", EntityArgumentType.players())
							.executes(context -> give(
									context.getSource(),
									EntityArgumentType.getPlayers(context, "targets"),
									tier))));
		}

		dispatcher.register(root);
	}

	private static int give(ServerCommandSource source, Collection<ServerPlayerEntity> targets, KitTier tier) {
		MinecraftServer server = source.getServer();
		int count = 0;

		for (ServerPlayerEntity target : targets) {
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
