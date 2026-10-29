package codes.nasty.ecorewards;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/** Per-player reward level: XP from mining and killing raises it, a higher level multiplies the money. */
public final class Progression {
	public record Progress(int level, long xp) {
		public static final Progress START = new Progress(1, 0);
		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("level").forGetter(Progress::level),
			Codec.LONG.fieldOf("xp").forGetter(Progress::xp)
		).apply(instance, Progress::new));
	}

	public static final AttachmentType<Progress> PROGRESS = AttachmentRegistry.create(
		EcoRewards.id("progress"),
		builder -> builder.persistent(Progress.CODEC).copyOnDeath().initializer(() -> Progress.START)
	);

	private Progression() {
	}

	public static Progress get(Player player) {
		Progress progress = player.getAttachedOrCreate(PROGRESS);
		int maxLevel = RewardConfig.get().levels.maxLevel;
		// The max level may have been lowered since the progress was saved.
		return progress.level() > maxLevel ? new Progress(maxLevel, 0) : progress;
	}

	public static void set(Player player, Progress progress) {
		player.setAttached(PROGRESS, progress);
	}

	/** XP needed to go from {@code level} to the next one. */
	public static long xpForNextLevel(int level) {
		RewardConfig.LevelSettings settings = RewardConfig.get().levels;
		Long fixed = settings.xpOverrides.get(Integer.toString(level));
		if (fixed != null) return fixed;
		double xp = settings.xpBase * Math.pow(settings.xpGrowth, level - 1) + settings.xpIncrement * (level - 1);
		return Math.max(1L, Math.min(Long.MAX_VALUE / 2, Math.round(xp)));
	}

	public static double multiplier(int level) {
		RewardConfig.MultiplierSettings settings = RewardConfig.get().multiplier;
		Double fixed = settings.overrides.get(Integer.toString(level));
		if (fixed != null) return fixed;
		double value = settings.base + settings.perLevel * (level - 1);
		if (settings.max > 0.0) value = Math.min(value, settings.max);
		return Math.max(0.0, value);
	}

	public static boolean isMaxLevel(int level) {
		return level >= RewardConfig.get().levels.maxLevel;
	}

	/** Adds XP and levels up as often as it is enough for. XP does not build up at the max level. */
	public static Progress addXp(Progress progress, long xp) {
		int level = progress.level();
		long total = progress.xp() + Math.max(0L, xp);
		while (!isMaxLevel(level) && total >= xpForNextLevel(level)) {
			total -= xpForNextLevel(level);
			level++;
		}
		return new Progress(level, isMaxLevel(level) ? 0L : total);
	}
}
