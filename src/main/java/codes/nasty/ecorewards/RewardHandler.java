package codes.nasty.ecorewards;

import codes.nasty.ecorewards.Progression.Progress;
import codes.nasty.ecorewards.RewardConfig.Reward;
import codes.nasty.ecorewards.RewardTable.Match;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import me.andy.ecobal.api.EconomyManager;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Decides what a mined block or killed mob is worth and pays the player. */
public final class RewardHandler {
	/** Entity tag given to mobs that come out of a monster spawner. */
	public static final String SPAWNER_TAG = EcoRewards.MOD_ID + ".spawner";
	private static final DecimalFormat MULTIPLIER_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

	/** Reward decided before the block was removed, paid once the break went through. */
	private static @Nullable PendingBreak pendingBreak;

	private record PendingBreak(ServerPlayer player, BlockPos pos, Reward reward) {
	}

	private RewardHandler() {
	}

	/**
	 * Runs before the block is removed: afterwards its artificial marker is gone and a tool that broke
	 * on this block is no longer in the player's hand.
	 */
	public static void beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state) {
		pendingBreak = null;
		if (!(player instanceof ServerPlayer serverPlayer) || !canEarn(serverPlayer)) return;
		RewardConfig config = RewardConfig.get();
		if (!config.blocks.enabled) return;
		Match<Reward> match = RewardTable.block(state.getBlock());
		if (match == null || match.value().isEmpty()) return;

		if (isCrop(state)) {
			// Planted crops are artificial by nature, growing them to full size is the work.
			if (config.blocks.matureCropsOnly && !isMatureCrop(state)) return;
		} else if (config.antiExploit.ignoreArtificialBlocks && ArtificialBlocks.isArtificial(level, pos)) {
			return;
		}
		if (match.fallback() && config.blocks.defaultSkipsInstantBreak && state.getDestroySpeed(level, pos) == 0.0F) return;
		if (config.blocks.requireCorrectTool && !player.hasCorrectToolForDrops(state)) return;

		pendingBreak = new PendingBreak(serverPlayer, pos.immutable(), match.value());
	}

	public static void afterBlockBreak(Player player, BlockPos pos) {
		PendingBreak pending = pendingBreak;
		pendingBreak = null;
		if (pending != null && pending.player() == player && pending.pos().equals(pos)) {
			grant(pending.player(), pending.reward().money, pending.reward().xp);
		}
	}

	public static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel)) return;
		RewardConfig config = RewardConfig.get();
		if (!config.mobs.enabled) return;
		ServerPlayer killer = findKiller(entity, source);
		if (killer == null || !canEarn(killer)) return;
		Match<Reward> match = RewardTable.mob(entity.getType());
		if (match == null || match.value().isEmpty()) return;

		double money = match.value().money;
		long xp = match.value().xp;
		if (entity.getTags().contains(SPAWNER_TAG)) {
			double factor = config.mobs.spawnerMultiplier;
			money *= factor;
			// Random rounding keeps the average right for small values (1 XP * 0.25 = a 25% chance of 1 XP).
			double scaledXp = xp * factor;
			xp = (long) Math.floor(scaledXp + entity.getRandom().nextDouble());
		}
		grant(killer, money, xp);
	}

	/** Pays {@code money} times the player's level multiplier and adds the XP, with feedback and level-up messages. */
	public static void grant(ServerPlayer player, double money, long xp) {
		Progress before = Progression.get(player);
		double multiplier = Progression.multiplier(before.level());
		double amount = Payouts.roundMoney(money * multiplier);
		Progress after = Progression.addXp(before, xp);
		if (!after.equals(before)) Progression.set(player, after);
		Payouts.add(player.getUUID(), amount);

		RewardConfig.MessageSettings messages = RewardConfig.get().messages;
		if (messages.showRewardActionBar && (amount > 0.0 || xp > 0)) {
			Map<String, String> placeholders = placeholders(player, after);
			placeholders.put("money", EconomyManager.formatBalance(amount));
			placeholders.put("gainedXp", Long.toString(xp));
			send(player, amount > 0.0 ? messages.reward : messages.rewardXpOnly, placeholders, true);
		}
		if (after.level() > before.level()) {
			onLevelUp(player, after);
		}
	}

	public static void onLevelUp(ServerPlayer player, Progress progress) {
		RewardConfig.MessageSettings messages = RewardConfig.get().messages;
		Map<String, String> placeholders = placeholders(player, progress);
		send(player, messages.levelUp, placeholders, false);
		if (messages.broadcastLevelUp && !messages.levelUpBroadcast.isEmpty()) {
			player.level().getServer().getPlayerList().broadcastSystemMessage(TextFormat.parse(TextFormat.fill(messages.levelUpBroadcast, placeholders)), false);
		}
		if (messages.levelUpSound) {
			player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP), SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(), 0.75F, 1.0F, player.getRandom().nextLong()
			));
		}
	}

	/** Level, progress and multiplier placeholders shared by all messages. */
	public static Map<String, String> placeholders(ServerPlayer player, Progress progress) {
		RewardConfig.MessageSettings messages = RewardConfig.get().messages;
		Map<String, String> placeholders = new LinkedHashMap<>();
		placeholders.put("player", player.getGameProfile().name());
		placeholders.put("level", Integer.toString(progress.level()));
		placeholders.put("multiplier", MULTIPLIER_FORMAT.format(Progression.multiplier(progress.level())));
		placeholders.put("xp", Long.toString(progress.xp()));
		if (Progression.isMaxLevel(progress.level())) {
			placeholders.put("xpNeeded", messages.progressMaxLevel);
			placeholders.put("progress", messages.progressMaxLevel);
		} else {
			String needed = Long.toString(Progression.xpForNextLevel(progress.level()));
			placeholders.put("xpNeeded", needed);
			placeholders.put("progress", TextFormat.fill(messages.progress, Map.of("xp", Long.toString(progress.xp()), "xpNeeded", needed)));
		}
		return placeholders;
	}

	public static void send(ServerPlayer player, String template, Map<String, String> placeholders, boolean actionBar) {
		if (!template.isEmpty()) {
			player.sendSystemMessage(TextFormat.parse(TextFormat.fill(template, placeholders)), actionBar);
		}
	}

	private static boolean canEarn(ServerPlayer player) {
		return !(player instanceof FakePlayer) && !player.isSpectator() && (!player.isCreative() || RewardConfig.get().rewardInCreative);
	}

	/** The player who dealt the killing blow, or who hit the mob last (knocked into lava, killed by a tamed wolf, ...). */
	private static @Nullable ServerPlayer findKiller(LivingEntity entity, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) return player;
		return entity.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
	}

	private static boolean isCrop(BlockState state) {
		Block block = state.getBlock();
		return block instanceof CropBlock || block instanceof NetherWartBlock || block instanceof CocoaBlock
			|| block instanceof SweetBerryBushBlock || block instanceof PitcherCropBlock;
	}

	private static boolean isMatureCrop(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) return crop.isMaxAge(state);
		if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
		if (block instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE;
		if (block instanceof PitcherCropBlock) return state.getValue(PitcherCropBlock.AGE) >= PitcherCropBlock.MAX_AGE;
		return true;
	}
}
