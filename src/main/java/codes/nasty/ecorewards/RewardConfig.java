package codes.nasty.ecorewards;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side settings, stored in {@code config/ecorewards.json}.
 * Unknown keys are ignored and missing keys fall back to the defaults below,
 * so the file is rewritten on every successful load to pick up new options.
 */
public final class RewardConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(EcoRewards.MOD_ID + ".json");
	private static RewardConfig instance = new RewardConfig();

	/** Players in creative mode earn nothing unless this is enabled. */
	public boolean rewardInCreative = false;
	/**
	 * Earned money is collected and paid out to EcoBal every this many seconds (0 = immediately).
	 * EcoBal writes its whole balance file on every deposit, so paying per block would hammer the disk.
	 */
	public int payoutIntervalSeconds = 3;
	/**
	 * Language of the texts for players who don't have the mod installed ("en_us" or "de_de"), see {@link ServerLang}.
	 * Messages that still have the default text of another language switch along.
	 */
	public String language = ServerLang.LANGUAGES.getFirst();

	public LevelSettings levels = new LevelSettings();
	public MultiplierSettings multiplier = new MultiplierSettings();
	public BlockSettings blocks = new BlockSettings();
	public MobSettings mobs = new MobSettings();
	public AntiExploitSettings antiExploit = new AntiExploitSettings();
	public MessageSettings messages = new MessageSettings();

	/** Base reward of one block or mob, before the level multiplier. */
	public static final class Reward {
		public double money;
		/** Progress points towards the next level (not vanilla experience). */
		public int xp;

		public Reward() {
		}

		public Reward(double money, int xp) {
			this.money = money;
			this.xp = xp;
		}

		public boolean isEmpty() {
			return money <= 0.0 && xp <= 0;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Reward reward && Double.compare(reward.money, this.money) == 0 && reward.xp == this.xp;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.money, this.xp);
		}
	}

	public static final class LevelSettings {
		public int maxLevel = 50;
		/** XP needed for level N to N+1 = xpBase * xpGrowth^(N-1) + xpIncrement * (N-1). */
		public double xpBase = 100.0;
		public double xpGrowth = 1.08;
		public double xpIncrement = 0.0;
		/** Fixed XP requirements per level, e.g. {"10": 5000} = level 10 to 11 needs 5000 XP. They win over the formula. */
		public LinkedHashMap<String, Long> xpOverrides = new LinkedHashMap<>();
	}

	public static final class MultiplierSettings {
		/** Money multiplier = base + perLevel * (level - 1), capped at max (0 = no cap). */
		public double base = 1.0;
		public double perLevel = 0.05;
		public double max = 0.0;
		/** Fixed multipliers per level, e.g. {"50": 5.0}. They win over the formula. */
		public LinkedHashMap<String, Double> overrides = new LinkedHashMap<>();
	}

	public static final class BlockSettings {
		public boolean enabled = true;
		/** Blocks that only drop with the right tool (ores, stone, ...) pay nothing when mined without it. */
		public boolean requireCorrectTool = true;
		/** Crops (wheat, carrots, nether wart, cocoa, ...) only pay when fully grown. */
		public boolean matureCropsOnly = true;
		/** The "default" entry skips blocks that break instantly (grass, flowers, torches, ...); listed blocks always count. */
		public boolean defaultSkipsInstantBreak = true;
		/**
		 * Rewards per block id or block tag ("#minecraft:logs"). A block id wins over tags,
		 * tags are checked from top to bottom, "default" applies to every other block.
		 */
		public LinkedHashMap<String, Reward> rewards = defaultBlockRewards();
	}

	public static final class MobSettings {
		public boolean enabled = true;
		/** Money and XP of mobs that came out of a monster spawner are multiplied by this. */
		public double spawnerMultiplier = 0.25;
		/** Rewards per entity id or entity tag ("#minecraft:undead"); same rules as for blocks. */
		public LinkedHashMap<String, Reward> rewards = defaultMobRewards();
	}

	public static final class AntiExploitSettings {
		/**
		 * Blocks that did not generate with the world pay nothing: placed by players, pushed by pistons,
		 * fallen sand, cobblestone/obsidian from lava and water, ice, snow golem trails, ...
		 * Crops are exempt as long as they are fully grown. Blocks set with commands (/setblock, /fill) count as natural.
		 */
		public boolean ignoreArtificialBlocks = true;
		/** Blocks that count as natural when they grow on their own (trees, sugar cane, melons, ...). Ids or tags. */
		public List<String> naturalGrowthBlocks = new ArrayList<>(List.of(
			"#minecraft:logs",
			"#minecraft:leaves",
			"#minecraft:wart_blocks",
			"minecraft:shroomlight",
			"minecraft:mushroom_stem",
			"minecraft:brown_mushroom_block",
			"minecraft:red_mushroom_block",
			"minecraft:mangrove_roots",
			"minecraft:muddy_mangrove_roots",
			"minecraft:sugar_cane",
			"minecraft:cactus",
			"minecraft:cactus_flower",
			"minecraft:bamboo",
			"minecraft:kelp",
			"minecraft:kelp_plant",
			"minecraft:melon",
			"minecraft:pumpkin",
			"minecraft:chorus_plant",
			"minecraft:chorus_flower",
			"minecraft:vine",
			"minecraft:cave_vines",
			"minecraft:cave_vines_plant",
			"minecraft:twisting_vines",
			"minecraft:twisting_vines_plant",
			"minecraft:weeping_vines",
			"minecraft:weeping_vines_plant",
			"minecraft:pale_hanging_moss",
			"minecraft:small_amethyst_bud",
			"minecraft:medium_amethyst_bud",
			"minecraft:large_amethyst_bud",
			"minecraft:amethyst_cluster",
			"minecraft:pointed_dripstone"
		));
	}

	/**
	 * Texts shown to players. Supports {@code &} color codes and {@code &#RRGGBB}; an empty text is not sent.
	 * Placeholders: {player}, {money}, {gainedXp}, {level}, {multiplier}, {xp}, {xpNeeded}, {progress}.
	 * Missing texts get the default of {@link #language} (language files, keys {@code ecorewards.message.*}).
	 */
	public static final class MessageSettings {
		public boolean showRewardActionBar = true;
		public String reward;
		public String rewardXpOnly;
		public String progress;
		public String progressMaxLevel;
		public String levelUp;
		public boolean levelUpSound = true;
		public boolean broadcastLevelUp = false;
		public String levelUpBroadcast;
		public String info;
		public String infoOther;
	}

	private static LinkedHashMap<String, Reward> defaultBlockRewards() {
		LinkedHashMap<String, Reward> map = new LinkedHashMap<>();
		map.put("default", new Reward(0.05, 1));
		map.put("minecraft:nether_quartz_ore", new Reward(2.0, 3));
		map.put("minecraft:nether_gold_ore", new Reward(2.0, 3));
		map.put("minecraft:ancient_debris", new Reward(50.0, 40));
		map.put("minecraft:spawner", new Reward(10.0, 10));
		map.put("minecraft:amethyst_cluster", new Reward(1.0, 2));
		map.put("minecraft:wheat", new Reward(0.5, 1));
		map.put("minecraft:carrots", new Reward(0.5, 1));
		map.put("minecraft:potatoes", new Reward(0.5, 1));
		map.put("minecraft:beetroots", new Reward(0.5, 1));
		map.put("minecraft:nether_wart", new Reward(0.5, 1));
		map.put("minecraft:cocoa", new Reward(0.5, 1));
		map.put("minecraft:melon", new Reward(0.4, 1));
		map.put("minecraft:pumpkin", new Reward(0.4, 1));
		map.put("minecraft:sugar_cane", new Reward(0.1, 1));
		map.put("minecraft:cactus", new Reward(0.1, 1));
		map.put("#minecraft:coal_ores", new Reward(2.0, 3));
		map.put("#minecraft:copper_ores", new Reward(1.5, 3));
		map.put("#minecraft:iron_ores", new Reward(3.0, 5));
		map.put("#minecraft:gold_ores", new Reward(5.0, 8));
		map.put("#minecraft:redstone_ores", new Reward(3.0, 5));
		map.put("#minecraft:lapis_ores", new Reward(4.0, 6));
		map.put("#minecraft:diamond_ores", new Reward(25.0, 20));
		map.put("#minecraft:emerald_ores", new Reward(30.0, 25));
		map.put("#minecraft:logs", new Reward(0.5, 2));
		map.put("#minecraft:leaves", new Reward(0.01, 0));
		return map;
	}

	private static LinkedHashMap<String, Reward> defaultMobRewards() {
		LinkedHashMap<String, Reward> map = new LinkedHashMap<>();
		map.put("default", new Reward(0.5, 1));
		map.put("minecraft:zombie", new Reward(2.0, 3));
		map.put("minecraft:husk", new Reward(2.0, 3));
		map.put("minecraft:drowned", new Reward(2.5, 3));
		map.put("minecraft:zombie_villager", new Reward(2.0, 3));
		map.put("minecraft:skeleton", new Reward(2.5, 3));
		map.put("minecraft:stray", new Reward(2.5, 3));
		map.put("minecraft:bogged", new Reward(2.5, 3));
		map.put("minecraft:creeper", new Reward(3.0, 4));
		map.put("minecraft:spider", new Reward(2.0, 3));
		map.put("minecraft:cave_spider", new Reward(2.0, 3));
		map.put("minecraft:enderman", new Reward(4.0, 5));
		map.put("minecraft:witch", new Reward(4.0, 5));
		map.put("minecraft:slime", new Reward(0.5, 1));
		map.put("minecraft:magma_cube", new Reward(0.5, 1));
		map.put("minecraft:silverfish", new Reward(0.5, 1));
		map.put("minecraft:endermite", new Reward(0.5, 1));
		map.put("minecraft:phantom", new Reward(3.0, 4));
		map.put("minecraft:pillager", new Reward(3.0, 4));
		map.put("minecraft:vindicator", new Reward(5.0, 6));
		map.put("minecraft:evoker", new Reward(15.0, 15));
		map.put("minecraft:vex", new Reward(1.0, 1));
		map.put("minecraft:ravager", new Reward(20.0, 20));
		map.put("minecraft:guardian", new Reward(4.0, 5));
		map.put("minecraft:elder_guardian", new Reward(75.0, 100));
		map.put("minecraft:zombified_piglin", new Reward(1.0, 2));
		map.put("minecraft:piglin", new Reward(2.0, 2));
		map.put("minecraft:piglin_brute", new Reward(8.0, 8));
		map.put("minecraft:hoglin", new Reward(3.0, 4));
		map.put("minecraft:zoglin", new Reward(3.0, 4));
		map.put("minecraft:blaze", new Reward(4.0, 5));
		map.put("minecraft:ghast", new Reward(6.0, 6));
		map.put("minecraft:wither_skeleton", new Reward(6.0, 6));
		map.put("minecraft:shulker", new Reward(5.0, 6));
		map.put("minecraft:breeze", new Reward(6.0, 6));
		map.put("minecraft:warden", new Reward(150.0, 150));
		map.put("minecraft:wither", new Reward(300.0, 300));
		map.put("minecraft:ender_dragon", new Reward(500.0, 500));
		map.put("minecraft:villager", new Reward(0.0, 0));
		map.put("minecraft:wandering_trader", new Reward(0.0, 0));
		map.put("minecraft:iron_golem", new Reward(0.0, 0));
		map.put("minecraft:snow_golem", new Reward(0.0, 0));
		return map;
	}

	public static RewardConfig get() {
		return instance;
	}

	/** Loads the config at startup; a broken file is reported and the defaults are used instead. */
	public static void load() {
		try {
			reload();
		} catch (IOException | JsonParseException e) {
			EcoRewards.LOGGER.error("Could not read {}, using default settings", PATH, e);
			apply(new RewardConfig());
		}
	}

	public static void reload() throws IOException {
		RewardConfig config = new RewardConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				RewardConfig parsed = GSON.fromJson(reader, RewardConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			}
		}
		apply(config);
		save(config);
	}

	/** Activates a config without touching the file (also used by the game tests). */
	public static void apply(RewardConfig config) {
		config.sanitize();
		instance = config;
		ServerLang.select(config.language);
		RewardTable.rebuild(config);
	}

	/** Activates settings changed in the config screen and writes them to the file. */
	public static void update(RewardConfig config) {
		apply(config);
		save(config);
	}

	/** The default settings with every message filled in. */
	public static RewardConfig defaults() {
		RewardConfig config = new RewardConfig();
		config.sanitize();
		return config;
	}

	/** A deep copy, for the config screen to change. */
	public RewardConfig copy() {
		return GSON.fromJson(GSON.toJsonTree(this), RewardConfig.class);
	}

	private static void save(RewardConfig config) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			EcoRewards.LOGGER.error("Could not write {}", PATH, e);
		}
	}

	private void sanitize() {
		payoutIntervalSeconds = Math.max(0, payoutIntervalSeconds);
		language = ServerLang.supported(language);

		if (levels == null) levels = new LevelSettings();
		levels.maxLevel = Math.max(1, levels.maxLevel);
		levels.xpBase = Math.max(1.0, finite(levels.xpBase, 100.0));
		levels.xpGrowth = Math.max(1.0, finite(levels.xpGrowth, 1.0));
		levels.xpIncrement = Math.max(0.0, finite(levels.xpIncrement, 0.0));
		if (levels.xpOverrides == null) levels.xpOverrides = new LinkedHashMap<>();
		levels.xpOverrides.replaceAll((level, xp) -> Math.max(1L, xp == null ? 1L : xp));

		if (multiplier == null) multiplier = new MultiplierSettings();
		multiplier.base = Math.max(0.0, finite(multiplier.base, 1.0));
		multiplier.perLevel = finite(multiplier.perLevel, 0.0);
		multiplier.max = Math.max(0.0, finite(multiplier.max, 0.0));
		if (multiplier.overrides == null) multiplier.overrides = new LinkedHashMap<>();
		multiplier.overrides.replaceAll((level, value) -> Math.max(0.0, value == null ? 0.0 : finite(value, 0.0)));

		if (blocks == null) blocks = new BlockSettings();
		blocks.rewards = sanitizeRewards(blocks.rewards);
		if (mobs == null) mobs = new MobSettings();
		mobs.spawnerMultiplier = Math.max(0.0, finite(mobs.spawnerMultiplier, 1.0));
		mobs.rewards = sanitizeRewards(mobs.rewards);

		if (antiExploit == null) antiExploit = new AntiExploitSettings();
		if (antiExploit.naturalGrowthBlocks == null) antiExploit.naturalGrowthBlocks = new ArrayList<>();

		if (messages == null) messages = new MessageSettings();
		messages.reward = ServerLang.template(language, "ecorewards.message.reward", messages.reward);
		messages.rewardXpOnly = ServerLang.template(language, "ecorewards.message.reward_xp_only", messages.rewardXpOnly);
		messages.progress = ServerLang.template(language, "ecorewards.message.progress", messages.progress);
		messages.progressMaxLevel = ServerLang.template(language, "ecorewards.message.progress_max_level", messages.progressMaxLevel);
		messages.levelUp = ServerLang.template(language, "ecorewards.message.level_up", messages.levelUp);
		messages.levelUpBroadcast = ServerLang.template(language, "ecorewards.message.level_up_broadcast", messages.levelUpBroadcast);
		messages.info = ServerLang.template(language, "ecorewards.message.info", messages.info);
		messages.infoOther = ServerLang.template(language, "ecorewards.message.info_other", messages.infoOther);
	}

	private static LinkedHashMap<String, Reward> sanitizeRewards(LinkedHashMap<String, Reward> rewards) {
		LinkedHashMap<String, Reward> result = new LinkedHashMap<>();
		if (rewards == null) return result;
		for (Map.Entry<String, Reward> entry : rewards.entrySet()) {
			Reward reward = entry.getValue() == null ? new Reward() : entry.getValue();
			reward.money = Math.max(0.0, finite(reward.money, 0.0));
			reward.xp = Math.max(0, reward.xp);
			result.put(entry.getKey(), reward);
		}
		return result;
	}

	private static double finite(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}
}
