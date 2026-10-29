package codes.nasty.ecorewards.client.config;

import codes.nasty.ecorewards.EcoRewards;
import codes.nasty.ecorewards.RewardConfig;
import codes.nasty.ecorewards.RewardConfig.Reward;
import codes.nasty.ecorewards.ServerLang;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The config button in Mod Menu's mod list. Mod Menu is optional; without it this class is never loaded. */
public class ModMenuIntegration implements ModMenuApi {
	private static final int MESSAGE_LENGTH = 256;
	private static final double MAX_DECIMAL = 1_000_000_000.0;
	/** Keys of the per-level maps: a level from 1 up. */
	private static final Predicate<String> LEVEL = key -> {
		try {
			return Integer.parseInt(key) >= 1;
		} catch (NumberFormatException e) {
			return false;
		}
	};

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModMenuIntegration::createScreen;
	}

	public static Screen createScreen(Screen parent) {
		return new ConfigScreen<>(EcoRewards.MOD_ID, parent, RewardConfig.get().copy(), RewardConfig.defaults(), RewardConfig::update)
			.section("general")
			.toggle("rewardInCreative", c -> c.rewardInCreative, (c, v) -> c.rewardInCreative = v)
			.integer("payoutIntervalSeconds", 0, 86_400, c -> c.payoutIntervalSeconds, (c, v) -> c.payoutIntervalSeconds = v)
			.choice("language", ServerLang.LANGUAGES, code -> Component.literal(ServerLang.text(code, "ecorewards.language.name")),
				c -> c.language, (c, v) -> c.language = v)
			.section("levels")
			.integer("levels.maxLevel", 1, 100_000, c -> c.levels.maxLevel, (c, v) -> c.levels.maxLevel = v)
			.decimal("levels.xpBase", 1.0, MAX_DECIMAL, c -> c.levels.xpBase, (c, v) -> c.levels.xpBase = v)
			.decimal("levels.xpGrowth", 1.0, 100.0, c -> c.levels.xpGrowth, (c, v) -> c.levels.xpGrowth = v)
			.decimal("levels.xpIncrement", 0.0, MAX_DECIMAL, c -> c.levels.xpIncrement, (c, v) -> c.levels.xpIncrement = v)
			.map("levels.xpOverrides", LEVEL, ModMenuIntegration::parseXp, String::valueOf,
				c -> c.levels.xpOverrides, (c, v) -> c.levels.xpOverrides = new LinkedHashMap<>(v))
			.section("multiplier")
			.decimal("multiplier.base", 0.0, MAX_DECIMAL, c -> c.multiplier.base, (c, v) -> c.multiplier.base = v)
			.decimal("multiplier.perLevel", -MAX_DECIMAL, MAX_DECIMAL, c -> c.multiplier.perLevel, (c, v) -> c.multiplier.perLevel = v)
			.decimal("multiplier.max", 0.0, MAX_DECIMAL, c -> c.multiplier.max, (c, v) -> c.multiplier.max = v)
			.map("multiplier.overrides", LEVEL, ModMenuIntegration::parseMultiplier, ConfigScreen::formatDecimal,
				c -> c.multiplier.overrides, (c, v) -> c.multiplier.overrides = new LinkedHashMap<>(v))
			.section("blocks")
			.toggle("blocks.enabled", c -> c.blocks.enabled, (c, v) -> c.blocks.enabled = v)
			.toggle("blocks.requireCorrectTool", c -> c.blocks.requireCorrectTool, (c, v) -> c.blocks.requireCorrectTool = v)
			.toggle("blocks.matureCropsOnly", c -> c.blocks.matureCropsOnly, (c, v) -> c.blocks.matureCropsOnly = v)
			.toggle("blocks.defaultSkipsInstantBreak", c -> c.blocks.defaultSkipsInstantBreak, (c, v) -> c.blocks.defaultSkipsInstantBreak = v)
			.map("blocks.rewards", ConfigScreen.ID_OR_TAG, ModMenuIntegration::parseReward, ModMenuIntegration::formatReward,
				c -> c.blocks.rewards, (c, v) -> c.blocks.rewards = new LinkedHashMap<>(v))
			.section("mobs")
			.toggle("mobs.enabled", c -> c.mobs.enabled, (c, v) -> c.mobs.enabled = v)
			.decimal("mobs.spawnerMultiplier", 0.0, 1000.0, c -> c.mobs.spawnerMultiplier, (c, v) -> c.mobs.spawnerMultiplier = v)
			.map("mobs.rewards", ConfigScreen.ID_OR_TAG, ModMenuIntegration::parseReward, ModMenuIntegration::formatReward,
				c -> c.mobs.rewards, (c, v) -> c.mobs.rewards = new LinkedHashMap<>(v))
			.section("antiExploit")
			.toggle("antiExploit.ignoreArtificialBlocks", c -> c.antiExploit.ignoreArtificialBlocks, (c, v) -> c.antiExploit.ignoreArtificialBlocks = v)
			.list("antiExploit.naturalGrowthBlocks", ConfigScreen.ID_OR_TAG,
				c -> c.antiExploit.naturalGrowthBlocks, (c, v) -> c.antiExploit.naturalGrowthBlocks = new ArrayList<>(v))
			.section("messages")
			.toggle("messages.showRewardActionBar", c -> c.messages.showRewardActionBar, (c, v) -> c.messages.showRewardActionBar = v)
			.text("messages.reward", MESSAGE_LENGTH, c -> c.messages.reward, (c, v) -> c.messages.reward = v)
			.text("messages.rewardXpOnly", MESSAGE_LENGTH, c -> c.messages.rewardXpOnly, (c, v) -> c.messages.rewardXpOnly = v)
			.text("messages.progress", MESSAGE_LENGTH, c -> c.messages.progress, (c, v) -> c.messages.progress = v)
			.text("messages.progressMaxLevel", MESSAGE_LENGTH, c -> c.messages.progressMaxLevel, (c, v) -> c.messages.progressMaxLevel = v)
			.text("messages.levelUp", MESSAGE_LENGTH, c -> c.messages.levelUp, (c, v) -> c.messages.levelUp = v)
			.toggle("messages.levelUpSound", c -> c.messages.levelUpSound, (c, v) -> c.messages.levelUpSound = v)
			.toggle("messages.broadcastLevelUp", c -> c.messages.broadcastLevelUp, (c, v) -> c.messages.broadcastLevelUp = v)
			.text("messages.levelUpBroadcast", MESSAGE_LENGTH, c -> c.messages.levelUpBroadcast, (c, v) -> c.messages.levelUpBroadcast = v)
			.text("messages.info", MESSAGE_LENGTH, c -> c.messages.info, (c, v) -> c.messages.info = v)
			.text("messages.infoOther", MESSAGE_LENGTH, c -> c.messages.infoOther, (c, v) -> c.messages.infoOther = v);
	}

	/** {@code money, xp} – the last comma separates them, so "2,5, 3" (German decimal comma) works too. */
	static Reward parseReward(String text) {
		int separator = text.lastIndexOf(',');
		if (separator < 0) return null;
		Double money = ConfigScreen.parseDecimal(text.substring(0, separator));
		try {
			int xp = Integer.parseInt(text.substring(separator + 1).trim());
			return money != null && money >= 0.0 && xp >= 0 ? new Reward(money, xp) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static String formatReward(Reward reward) {
		return ConfigScreen.formatDecimal(reward.money) + ", " + reward.xp;
	}

	private static Long parseXp(String text) {
		try {
			long xp = Long.parseLong(text.trim());
			return xp >= 1 ? xp : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Double parseMultiplier(String text) {
		Double value = ConfigScreen.parseDecimal(text);
		return value != null && value >= 0.0 ? value : null;
	}
}
