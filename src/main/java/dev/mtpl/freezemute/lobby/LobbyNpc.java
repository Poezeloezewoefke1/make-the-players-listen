package dev.mtpl.freezemute.lobby;

import java.util.List;
import java.util.Optional;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FreezeMuteConfig;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * The figure standing on the pedestal, with its sign floating over its head.
 *
 * <p>An armour stand rather than a fake player. A real NPC with a downloaded skin means holding a
 * fake entry in the player list, a spawn packet per viewer and a texture fetched from Mojang at
 * runtime - three things that break in different ways on a server that is meant to depend on
 * nothing but the loader. An armour stand is one entity, needs nothing from outside, and every
 * client already knows how to draw one.
 *
 * <p>What it wears is left to the config, because "cool" is not a thing this file can decide. The
 * head is any item id - {@code dragon_head} by default, which is dramatic and always exists, but
 * {@code player_head} works too for anybody who wants to point it at a real skin afterwards - and
 * the armour is a material name. Anything that does not resolve is skipped with a line in the log
 * rather than leaving a half dressed stand and no explanation.
 *
 * <p>It is only a signpost. Joining the queue is judged by where the player is standing when they
 * right click, not by what they hit, so the stand can be replaced with anything at all - or taken
 * away entirely - and the pedestal still works.
 */
public final class LobbyNpc {
	/** How close a stand has to be to the pedestal to count as the one already standing there. */
	private static final double SAME_SPOT = 3.0D;
	/** What it says when nobody has said otherwise. */
	public static final String DEFAULT_SIGN = "Join record!";

	private LobbyNpc() {
	}

	/**
	 * Puts one on the queue point, replacing whatever is already standing there.
	 *
	 * @return the stand, or null when there is nowhere to put it
	 */
	public static ArmorStandEntity place(ServerWorld world, Spot spot) {
		if (world == null || spot == null) {
			return null;
		}

		clear(world, spot);

		FreezeMuteConfig config = FreezeMuteConfig.get();
		ArmorStandEntity stand = new ArmorStandEntity(world, spot.x(), spot.y(), spot.z());

		stand.refreshPositionAndAngles(spot.x(), spot.y(), spot.z(), spot.yaw(), 0.0F);
		stand.setHeadYaw(spot.yaw());
		stand.setCustomName(sign(config.lobbyNpcText));
		stand.setCustomNameVisible(true);
		// Nothing about it should be knockable over, walkable through, or subject to gravity: it
		// is scenery, and scenery that drifts off its pedestal is worse than none.
		stand.setInvulnerable(true);
		stand.setNoGravity(true);
		stand.setHideBasePlate(true);
		stand.setShowArms(true);

		wear(stand, EquipmentSlot.HEAD, config.lobbyNpcHead);
		wear(stand, EquipmentSlot.CHEST, config.lobbyNpcArmour + "_chestplate");
		wear(stand, EquipmentSlot.LEGS, config.lobbyNpcArmour + "_leggings");
		wear(stand, EquipmentSlot.FEET, config.lobbyNpcArmour + "_boots");
		wear(stand, EquipmentSlot.MAINHAND, config.lobbyNpcHeld);

		if (!world.spawnEntity(stand)) {
			FreezeMute.LOGGER.warn("Lobby: the world refused the queue point figure");
			return null;
		}

		FreezeMute.LOGGER.info("Lobby: put a figure on the queue point at {}", spot.describe());
		return stand;
	}

	/**
	 * Takes away any armour stand standing on the queue point.
	 *
	 * @return how many were removed
	 */
	public static int clear(ServerWorld world, Spot spot) {
		if (world == null || spot == null) {
			return 0;
		}

		// List<? extends T>, not List<T> - the wildcard is in the real signature and reading it
		// away is how this compiled locally against a stub and failed against Minecraft.
		List<? extends ArmorStandEntity> standing = world.getEntitiesByType(EntityType.ARMOR_STAND,
				stand -> near(stand, spot));
		int removed = 0;

		for (ArmorStandEntity stand : standing) {
			stand.discard();
			removed++;
		}

		return removed;
	}

	/**
	 * The name that floats over it.
	 *
	 * <p>Bold, and gold so it reads against both the sky and the plaza. An armour stand's custom
	 * name is drawn through walls at close range and shrinks with distance, which is exactly the
	 * behaviour wanted from a sign over the thing you are meant to walk up to.
	 */
	static Text sign(String words) {
		return Text.literal(signText(words)).formatted(Formatting.BOLD, Formatting.GOLD);
	}

	/** The words themselves, with the fallback, kept apart so it can be tested without a client. */
	static String signText(String words) {
		return words == null || words.isBlank() ? DEFAULT_SIGN : words;
	}

	/** Whether a stand is close enough to the pedestal to be the one already on it. */
	private static boolean near(ArmorStandEntity stand, Spot spot) {
		double dx = stand.getX() - spot.x();
		double dy = stand.getY() - spot.y();
		double dz = stand.getZ() - spot.z();
		return dx * dx + dy * dy + dz * dz <= SAME_SPOT * SAME_SPOT;
	}

	/**
	 * Puts one item on it, or says in the log why it could not.
	 *
	 * <p>Item ids move between versions and this one came out of a config file, so a name that
	 * does not resolve is a line in the log rather than a crash or, worse, a half dressed figure
	 * with nothing anywhere saying why.
	 */
	private static void wear(ArmorStandEntity stand, EquipmentSlot slot, String id) {
		if (id == null || id.isBlank() || "none".equalsIgnoreCase(id)) {
			return;
		}

		Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);

		if (identifier == null) {
			FreezeMute.LOGGER.warn("Lobby: '{}' is not an item id, so the figure's {} is empty", id, slot);
			return;
		}

		Optional<Item> item = Registries.ITEM.getOptionalValue(identifier);

		if (item.isEmpty()) {
			FreezeMute.LOGGER.warn("Lobby: this version has no item called '{}', so the figure's {} is empty",
					id, slot);
			return;
		}

		stand.equipStack(slot, new ItemStack(item.get()));
	}
}
