package dev.mtpl.freezemute.lobby;

/**
 * The blocks the island is made of, named without reference to Minecraft.
 *
 * <p>Keeping the plan in these terms is what lets the shape of the island be worked out - and
 * checked - without a world to put it in. {@link LobbyBuilder} decides where every block goes
 * using nothing but this enum, and only the last step turns them into real blocks.
 */
public enum Material {
	AIR,
	WATER,
	STONE,
	ANDESITE,
	POLISHED_ANDESITE,
	SMOOTH_STONE,
	MOSSY_COBBLESTONE,
	DIRT,
	GRASS,
	SAND,
	JUNGLE_LOG,
	JUNGLE_LEAVES,
	OAK_LOG,
	OAK_PLANKS,
	OAK_FENCE,
	WHITE_CONCRETE,
	RED_CONCRETE,
	WHITE_WOOL,
	RED_WOOL,
	BLUE_WOOL,
	ORANGE_WOOL,
	YELLOW_WOOL,
	SEA_LANTERN,
	POLISHED_BLACKSTONE,
	POLISHED_BLACKSTONE_BRICKS,
	QUARTZ,
	GOLD,
	EMERALD,
	DIAMOND;

	/** Whether water is held back by it. Fences and leaves are not walls, but they are not air. */
	public boolean holdsWater() {
		return this != AIR && this != WATER && this != OAK_FENCE && this != JUNGLE_LEAVES;
	}

	/** Whether somebody can stand on it. */
	public boolean standable() {
		return this != AIR && this != WATER;
	}
}
