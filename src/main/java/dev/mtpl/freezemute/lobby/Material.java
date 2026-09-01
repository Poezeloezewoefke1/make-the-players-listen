package dev.mtpl.freezemute.lobby;

/**
 * The blocks the island is made of, named without reference to Minecraft.
 *
 * <p>Keeping the plan in these terms is what lets the shape of the island be worked out - and
 * checked - without a world to put it in. {@link LobbyBuilder} decides where every block goes
 * using nothing but this enum, and only the last step turns them into real blocks.
 *
 * <p>Everything here is a plain full block with a usable default state. Stairs, slabs, doors and
 * anything else that needs a facing or a half would have to carry that through the plan, the
 * tests and the placer, and the island is built out of shapes rather than out of joinery. The
 * names are also all long-standing ones: this enum is turned into real blocks in one switch, and
 * that switch is only ever compiled against real mappings in CI, so a block that got renamed
 * between versions is a build failure somebody has to go and look at.
 */
public enum Material {
	AIR,
	WATER,

	// Stone and the things cut from it.
	STONE,
	COBBLESTONE,
	MOSSY_COBBLESTONE,
	ANDESITE,
	POLISHED_ANDESITE,
	DIORITE,
	POLISHED_DIORITE,
	GRANITE,
	POLISHED_GRANITE,
	SMOOTH_STONE,
	STONE_BRICKS,
	MOSSY_STONE_BRICKS,
	CRACKED_STONE_BRICKS,
	CHISELED_STONE_BRICKS,
	DEEPSLATE,
	POLISHED_DEEPSLATE,
	DEEPSLATE_BRICKS,
	TUFF,
	CALCITE,
	BLACKSTONE,
	POLISHED_BLACKSTONE,
	POLISHED_BLACKSTONE_BRICKS,
	BRICKS,

	// Ground.
	DIRT,
	COARSE_DIRT,
	PODZOL,
	GRASS,
	MOSS_BLOCK,
	SAND,
	SANDSTONE,
	SMOOTH_SANDSTONE,
	GRAVEL,
	CLAY,

	// Wood.
	OAK_LOG,
	OAK_PLANKS,
	OAK_FENCE,
	OAK_LEAVES,
	STRIPPED_OAK_LOG,
	SPRUCE_LOG,
	SPRUCE_PLANKS,
	SPRUCE_FENCE,
	SPRUCE_LEAVES,
	DARK_OAK_LOG,
	DARK_OAK_PLANKS,
	BIRCH_LOG,
	BIRCH_PLANKS,
	BIRCH_LEAVES,
	JUNGLE_LOG,
	JUNGLE_PLANKS,
	JUNGLE_LEAVES,
	BOOKSHELF,

	// Colour, for the things meant to be seen from a long way off.
	WHITE_CONCRETE,
	RED_CONCRETE,
	BLUE_CONCRETE,
	YELLOW_CONCRETE,
	ORANGE_CONCRETE,
	LIME_CONCRETE,
	BLACK_CONCRETE,
	WHITE_WOOL,
	RED_WOOL,
	BLUE_WOOL,
	ORANGE_WOOL,
	YELLOW_WOOL,
	GREEN_WOOL,
	PURPLE_WOOL,
	WHITE_TERRACOTTA,
	ORANGE_TERRACOTTA,
	RED_TERRACOTTA,

	// The sea and the things built out over it.
	PRISMARINE,
	PRISMARINE_BRICKS,
	DARK_PRISMARINE,
	COPPER_BLOCK,
	OXIDIZED_COPPER,
	CUT_COPPER,

	// Light. A void world with no sky at night is a room nobody can see the far side of.
	SEA_LANTERN,
	GLOWSTONE,
	SHROOMLIGHT,
	OCHRE_FROGLIGHT,
	VERDANT_FROGLIGHT,

	// Glass, for the buildings that are meant to be looked into.
	GLASS,
	WHITE_STAINED_GLASS,
	LIGHT_BLUE_STAINED_GLASS,
	BROWN_STAINED_GLASS,

	// The parkour, and the prizes at the end of it.
	QUARTZ,
	CHISELED_QUARTZ,
	QUARTZ_BRICKS,
	GOLD,
	EMERALD,
	DIAMOND,
	AMETHYST,
	LAPIS,
	IRON,
	HAY;

	/**
	 * Whether water is held back by it.
	 *
	 * <p>Leaves and fences are not walls: water runs straight through both. Everything else here
	 * is a full cube, so everything else holds.
	 */
	public boolean holdsWater() {
		return switch (this) {
			case AIR, WATER, OAK_FENCE, SPRUCE_FENCE,
					OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES -> false;
			default -> true;
		};
	}

	/** Whether somebody can stand on it. */
	public boolean standable() {
		return this != AIR && this != WATER;
	}

	/** Whether it gives off light, which is what decides where the lamps have to go. */
	public boolean glows() {
		return switch (this) {
			case SEA_LANTERN, GLOWSTONE, SHROOMLIGHT, OCHRE_FROGLIGHT, VERDANT_FROGLIGHT -> true;
			default -> false;
		};
	}
}
