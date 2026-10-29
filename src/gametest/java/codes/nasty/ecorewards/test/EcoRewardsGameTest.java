package codes.nasty.ecorewards.test;

import codes.nasty.ecorewards.ArtificialBlocks;
import codes.nasty.ecorewards.Progression;
import codes.nasty.ecorewards.Progression.Progress;
import codes.nasty.ecorewards.RewardConfig;
import codes.nasty.ecorewards.RewardConfig.Reward;
import codes.nasty.ecorewards.RewardHandler;
import codes.nasty.ecorewards.ServerLang;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.UUID;
import me.andy.ecobal.api.EconomyManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.MixinEnvironment;

public class EcoRewardsGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	/** Fixed numbers so the tests don't depend on the defaults: level N multiplies money by 1 + 0.5 * (N - 1). */
	private static void useTestConfig() {
		RewardConfig config = new RewardConfig();
		config.payoutIntervalSeconds = 0;
		config.levels.maxLevel = 10;
		config.levels.xpBase = 100.0;
		config.levels.xpGrowth = 1.0;
		config.multiplier.base = 1.0;
		config.multiplier.perLevel = 0.5;
		config.blocks.rewards = new LinkedHashMap<>();
		config.blocks.rewards.put("default", new Reward(0.05, 1));
		config.blocks.rewards.put("minecraft:diamond_ore", new Reward(10.0, 20));
		config.blocks.rewards.put("minecraft:wheat", new Reward(1.0, 1));
		config.mobs.rewards = new LinkedHashMap<>();
		config.mobs.rewards.put("default", new Reward(0.5, 1));
		config.mobs.rewards.put("minecraft:zombie", new Reward(4.0, 4));
		config.mobs.spawnerMultiplier = 0.25;
		RewardConfig.apply(config);
	}

	@GameTest
	public void naturalOrePaysTimesLevelMultiplier(GameTestHelper helper) {
		useTestConfig();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		BlockPos pos = helper.absolutePos(POS);
		double start = balance(player);

		helper.setBlock(POS, Blocks.DIAMOND_ORE);
		helper.assertTrue(ArtificialBlocks.isArtificial(level, pos), "A block that appears while the world runs should be artificial");
		ArtificialBlocks.unmark(level, pos); // stands in for world generation
		player.gameMode.destroyBlock(pos);
		helper.assertBlockPresent(Blocks.AIR, POS);
		assertBalance(helper, player, start + 10.0);
		helper.assertTrue(Progression.get(player).equals(new Progress(1, 20)), "Expected level 1 with 20 XP, got " + Progression.get(player));

		Progression.set(player, new Progress(3, 0));
		helper.setBlock(POS, Blocks.DIAMOND_ORE);
		ArtificialBlocks.unmark(level, pos);
		player.gameMode.destroyBlock(pos);
		assertBalance(helper, player, start + 10.0 + 20.0);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void oreMinedWithoutToolPaysNothing(GameTestHelper helper) {
		useTestConfig();
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = helper.absolutePos(POS);
		double start = balance(player);

		helper.setBlock(POS, Blocks.DIAMOND_ORE);
		ArtificialBlocks.unmark(helper.getLevel(), pos);
		player.gameMode.destroyBlock(pos);
		helper.assertBlockPresent(Blocks.AIR, POS);
		assertBalance(helper, player, start);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void placedBlockPaysNothing(GameTestHelper helper) {
		useTestConfig();
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = helper.absolutePos(POS);
		double start = balance(player);

		ItemStack ore = new ItemStack(Items.DIAMOND_ORE);
		player.setItemInHand(InteractionHand.MAIN_HAND, ore);
		helper.placeAt(player, ore, POS.below(), Direction.UP);
		helper.assertBlockPresent(Blocks.DIAMOND_ORE, POS);
		helper.assertTrue(ArtificialBlocks.isArtificial(helper.getLevel(), pos), "A placed block should be artificial");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		player.gameMode.destroyBlock(pos);
		helper.assertBlockPresent(Blocks.AIR, POS);
		assertBalance(helper, player, start);
		helper.assertTrue(Progression.get(player).equals(Progress.START), "A placed block should give no XP");
		helper.assertFalse(ArtificialBlocks.isArtificial(helper.getLevel(), pos), "Breaking should clear the marker");

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void plantedCropPaysOnlyWhenGrown(GameTestHelper helper) {
		useTestConfig();
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = helper.absolutePos(POS);
		double start = balance(player);

		helper.setBlock(POS.below(), Blocks.FARMLAND);
		ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS);
		player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
		helper.placeAt(player, seeds, POS.below(), Direction.UP);
		helper.assertBlockPresent(Blocks.WHEAT, POS);
		helper.assertTrue(ArtificialBlocks.isArtificial(helper.getLevel(), pos), "Planted seeds should be artificial");
		player.gameMode.destroyBlock(pos);
		assertBalance(helper, player, start);

		helper.setBlock(POS, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
		helper.assertTrue(ArtificialBlocks.isArtificial(helper.getLevel(), pos), "Replanted wheat should be artificial");
		player.gameMode.destroyBlock(pos);
		assertBalance(helper, player, start + 1.0);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void mobKillPaysAndSpawnerMobsPayLess(GameTestHelper helper) {
		useTestConfig();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper);
		double start = balance(player);

		Zombie zombie = helper.spawn(EntityType.ZOMBIE, POS);
		zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
		helper.assertTrue(zombie.isDeadOrDying(), "Zombie should be dead");
		assertBalance(helper, player, start + 4.0);

		Zombie spawnerZombie = helper.spawn(EntityType.ZOMBIE, POS);
		spawnerZombie.addTag(RewardHandler.SPAWNER_TAG);
		spawnerZombie.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
		assertBalance(helper, player, start + 4.0 + 1.0);

		// Killed by something else after the player hit it: the player still gets the credit.
		Zombie knockedZombie = helper.spawn(EntityType.ZOMBIE, POS);
		knockedZombie.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
		knockedZombie.hurtServer(level, level.damageSources().lava(), 1000.0F);
		assertBalance(helper, player, start + 4.0 + 1.0 + 4.0);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void xpRaisesLevels(GameTestHelper helper) {
		useTestConfig();
		helper.assertTrue(Progression.addXp(Progress.START, 250).equals(new Progress(3, 50)), "250 XP should reach level 3 with 50 left");
		helper.assertTrue(Progression.addXp(new Progress(9, 0), 10_000).equals(new Progress(10, 0)), "XP should stop at the max level");
		helper.assertTrue(Progression.multiplier(1) == 1.0 && Progression.multiplier(3) == 2.0, "Multiplier should be 1 + 0.5 per level");
		helper.succeed();
	}

	@GameTest
	public void lavaAndWaterMakeArtificialObsidian(GameTestHelper helper) {
		useTestConfig();
		helper.setBlock(POS, Blocks.LAVA);
		helper.setBlock(POS.east(), Blocks.WATER);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OBSIDIAN, POS);
			helper.assertTrue(ArtificialBlocks.isArtificial(helper.getLevel(), helper.absolutePos(POS)), "Obsidian from lava and water should be artificial");
		});
	}

	@GameTest
	public void pistonPushedBlockIsArtificial(GameTestHelper helper) {
		useTestConfig();
		BlockPos ore = POS.east();
		helper.setBlock(POS, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(ore, Blocks.DIAMOND_ORE);
		ArtificialBlocks.unmark(helper.getLevel(), helper.absolutePos(ore));
		helper.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.DIAMOND_ORE, ore.east());
			helper.assertTrue(ArtificialBlocks.isArtificial(helper.getLevel(), helper.absolutePos(ore.east())), "A pushed block should be artificial");
		});
	}

	@GameTest
	public void blocksSetByCommandsAreNatural(GameTestHelper helper) {
		useTestConfig();
		BlockPos pos = helper.absolutePos(POS);
		MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:diamond_ore");
		helper.assertBlockPresent(Blocks.DIAMOND_ORE, POS);
		helper.assertFalse(ArtificialBlocks.isArtificial(helper.getLevel(), pos), "/setblock should count as natural");
		helper.succeed();
	}

	@GameTest
	public void grownBlocksAreNaturalButKeepTheirMarker(GameTestHelper helper) {
		useTestConfig();
		ServerLevel level = helper.getLevel();
		helper.setBlock(POS, Blocks.OAK_LOG);
		helper.assertFalse(ArtificialBlocks.isArtificial(level, helper.absolutePos(POS)), "A log growing into air should be natural");

		// A placed log stays artificial after stripping it.
		ArtificialBlocks.mark(level, helper.absolutePos(POS));
		helper.setBlock(POS, Blocks.STRIPPED_OAK_LOG);
		helper.assertTrue(ArtificialBlocks.isArtificial(level, helper.absolutePos(POS)), "Stripping must not clear the marker");
		helper.succeed();
	}

	@GameTest
	public void allMixinsApply(GameTestHelper helper) {
		MixinEnvironment.getCurrentEnvironment().audit();
		helper.succeed();
	}

	/** Unchanged default messages switch to the server language, edited ones stay; command texts fall back to it. */
	@GameTest
	public void messagesFollowTheServerLanguage(GameTestHelper helper) {
		RewardConfig config = new RewardConfig();
		config.language = "de_de";
		config.messages.levelUp = ServerLang.text("en_us", "ecorewards.message.level_up");
		config.messages.info = "&7My own text";
		RewardConfig.apply(config);
		helper.assertValueEqual(config.messages.levelUp, ServerLang.text("de_de", "ecorewards.message.level_up"), "level-up message in German");
		helper.assertValueEqual(config.messages.info, "&7My own text", "an edited message stays");
		helper.assertTrue(config.messages.levelUpBroadcast.contains("erreicht"), "missing messages get the German default");
		Component reloaded = ServerLang.translatable("ecorewards.command.reloaded");
		helper.assertTrue(reloaded.getContents() instanceof TranslatableContents contents && "EcoRewards-Konfiguration neu geladen.".equals(contents.getFallback()),
			"command texts fall back to the server language");

		config.language = "fr_fr";
		RewardConfig.apply(config);
		helper.assertValueEqual(config.language, "en_us", "unknown languages fall back to English");
		helper.assertValueEqual(config.messages.levelUp, ServerLang.text("en_us", "ecorewards.message.level_up"), "back to the English message");
		helper.succeed();
	}

	/** A real survival-mode player; the vanilla mock player is always in creative mode. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ecorewards-test"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos standing = helper.absolutePos(new BlockPos(5, 1, 5));
		player.snapTo(standing.getX() + 0.5, standing.getY(), standing.getZ() + 0.5);
		return player;
	}

	private static void removePlayer(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	/** Reads the balance, creating the EcoBal account first so later deposits don't add the starting balance. */
	private static double balance(ServerPlayer player) {
		return EconomyManager.getBalance(player.getUUID());
	}

	private static void assertBalance(GameTestHelper helper, ServerPlayer player, double expected) {
		double actual = EconomyManager.getPlayerBalance(player.getUUID());
		helper.assertTrue(Math.abs(actual - expected) < 0.001, "Expected balance " + expected + " but was " + actual);
	}
}
