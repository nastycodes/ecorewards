package codes.nasty.ecorewards.test;

import codes.nasty.ecorewards.EcoRewards;
import codes.nasty.ecorewards.Progression;
import codes.nasty.ecorewards.Progression.Progress;
import codes.nasty.ecorewards.RewardConfig;
import codes.nasty.ecorewards.RewardConfig.Reward;
import codes.nasty.ecorewards.ServerLang;
import codes.nasty.ecorewards.client.config.ConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.terraformersmc.modmenu.ModMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import me.andy.ecobal.api.EconomyManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * End-to-end check in a real client (English, survival, 1920x1080) with EcoBal:
 * mines a natural diamond ore (action-bar reward), levels up with the next one, kills a husk, runs /lvl, then changes
 * the settings through the screen behind Mod Menu's config button, including a reward map and the server language.
 * Run with {@code gradlew runClientGameTest}; screenshots land in build/run/clientGameTest/screenshots.
 */
public class EcoRewardsClientGameTest implements FabricClientGameTest {
	/** The diamond ore in the wall in front of the player; set on the server thread before anything reads it. */
	private static volatile BlockPos ore;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> client.getLanguageManager().setSelected("en_us"));
		context.runOnClient(client -> client.reloadResourcePacks());
		context.waitFor(client -> client.getOverlay() == null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.setAllowCommands(true))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runOnServer(EcoRewardsClientGameTest::prepare);
			buildWall(server);
			context.waitTicks(20);

			// A natural diamond ore pays 25 (level 1, multiplier 1) and 20 XP; the action bar shows it.
			double start = server.computeOnServer(s -> EconomyManager.getBalance(player(s).getUUID()));
			clearHud(context);
			mineOre(context);
			assertTrue(server.computeOnServer(s -> s.overworld().getBlockState(ore).isAir()), "the ore is mined");
			assertMoney(server, start + 25.0, "after the first ore");
			assertTrue(server.computeOnServer(s -> Progression.get(player(s))).equals(new Progress(1, 20)), "level 1 with 20 XP");
			screenshot(context, "ecorewards_01_ore_reward");

			// 75 more XP, then the next ore crosses the 100 XP of level 1: level up message in the chat.
			server.runCommand("ecorewards addxp " + playerName(server) + " 75");
			server.runCommand("setblock " + ore.getX() + " " + ore.getY() + " " + ore.getZ() + " minecraft:diamond_ore");
			context.waitTicks(5);
			clearHud(context);
			mineOre(context);
			assertTrue(server.computeOnServer(s -> Progression.get(player(s))).equals(new Progress(2, 15)), "level 2 with 15 XP");
			assertMoney(server, start + 50.0, "after the second ore");
			screenshot(context, "ecorewards_02_level_up");

			// A husk killed by the player pays its reward times the level-2 multiplier (2 x 1.05).
			server.runOnServer(EcoRewardsClientGameTest::spawnHusk);
			context.waitTicks(10);
			clearHud(context);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(3);
			screenshot(context, "ecorewards_03_mob_reward");
			context.waitTicks(5);
			assertMoney(server, start + 52.1, "after killing the husk");

			// /lvl as the player shows level, progress and multiplier (after the husk's death animation).
			context.waitTicks(40);
			clearHud(context);
			context.runOnClient(client -> client.player.connection.sendCommand("lvl"));
			context.waitTicks(10);
			screenshot(context, "ecorewards_04_lvl");

			checkConfigScreen(context, server);
			EcoRewards.LOGGER.info("[gametest] all checks passed");
		}
	}

	private static void prepare(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
		level.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);

		RewardConfig config = RewardConfig.defaults();
		config.payoutIntervalSeconds = 0;
		RewardConfig.update(config);

		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
		player.getInventory().setSelectedSlot(0);
		Progression.set(player, Progress.START);
		EconomyManager.updatePlayerNameCache(player.getUUID(), player.getGameProfile().name());

		BlockPos feet = player.blockPosition();
		ore = feet.east(4).above();
		// Look east, straight at the ore at eye level.
		player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, -90.0F, 0.0F);
	}

	/** A small stone wall with a few ores, set with commands like an admin-built mine, so it counts as natural. */
	private static void buildWall(TestServerContext server) {
		BlockPos bottom = ore.below();
		server.runCommand("fill " + bottom.getX() + " " + bottom.getY() + " " + (bottom.getZ() - 2) + " "
			+ bottom.getX() + " " + (bottom.getY() + 2) + " " + (bottom.getZ() + 2) + " minecraft:stone");
		server.runCommand("setblock " + ore.getX() + " " + ore.getY() + " " + ore.getZ() + " minecraft:diamond_ore");
		server.runCommand("setblock " + ore.getX() + " " + (ore.getY() + 1) + " " + (ore.getZ() - 1) + " minecraft:coal_ore");
		server.runCommand("setblock " + ore.getX() + " " + (ore.getY() - 1) + " " + (ore.getZ() + 2) + " minecraft:iron_ore");
		server.runCommand("setblock " + ore.getX() + " " + ore.getY() + " " + (ore.getZ() - 2) + " minecraft:gold_ore");
	}

	/** A husk with one heart left in front of the wall; the player looks down at it. */
	private static void spawnHusk(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Husk husk = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
		BlockPos stand = ore.below().west();
		husk.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 90.0F, 0.0F);
		husk.setNoAi(true);
		husk.setHealth(1.0F);
		level.addFreshEntity(husk);
		ServerPlayer player = player(server);
		player.connection.teleport(player.getX(), player.getY(), player.getZ(), -90.0F, 25.0F);
	}

	private static void mineOre(ClientGameTestContext context) {
		context.getInput().holdKeyFor(options -> options.keyAttack, 40);
		context.waitTicks(5);
	}

	/** Changes values, a reward map and the server language through the screen, then checks Esc discards. */
	private static void checkConfigScreen(ClientGameTestContext context, TestServerContext server) {
		context.setScreen(() -> ModMenu.getConfigScreen(EcoRewards.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		context.waitTicks(5);
		EditBox payout = widget(context, EditBox.class, "config.ecorewards.payoutIntervalSeconds", 0);
		// The second cycle button is the language (the first one is "rewards in creative mode").
		CycleButton<?> language = widget(context, CycleButton.class, null, 1);
		hoverWidget(context, language);
		screenshot(context, "ecorewards_05_config");

		clickWidget(context, language);
		context.runOnClient(client -> payout.setValue("5"));
		context.waitTicks(2);

		// Map editors in screen order: xp overrides, multiplier overrides, block rewards, mob rewards.
		Button blockRewards = widget(context, Button.class, "config.ecorewards.ui.edit", 2);
		scrollTo(context, blockRewards);
		clickWidget(context, blockRewards);
		context.waitFor(client -> !(client.screen instanceof ConfigScreen));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.getInput().typeChars("minecraft:dirt = 0,5, 2");
		context.waitTicks(3);
		screenshot(context, "ecorewards_06_reward_editor");
		context.clickScreenButton("gui.done");
		context.waitForScreen(ConfigScreen.class);
		context.waitTicks(3);
		context.clickScreenButton("gui.done");
		context.waitForScreen(null);
		context.waitTicks(10);

		assertTrue(server.computeOnServer(s -> RewardConfig.get().payoutIntervalSeconds) == 5, "payout interval saved through the screen");
		assertTrue(server.computeOnServer(s -> new Reward(0.5, 2).equals(RewardConfig.get().blocks.rewards.get("minecraft:dirt"))), "dirt reward added");
		assertTrue(server.computeOnServer(s -> RewardConfig.get().language).equals("de_de"), "server language switched to German");
		assertTrue(server.computeOnServer(s -> RewardConfig.get().messages.levelUp).equals(ServerLang.text("de_de", "ecorewards.message.level_up")),
			"the unchanged level-up message followed the language");
		String file = readConfigFile();
		assertTrue(file.contains("\"payoutIntervalSeconds\": 5") && file.contains("minecraft:dirt"), "the screen wrote config/ecorewards.json");

		// Esc with unsaved changes asks first; discarding keeps the saved values.
		context.setScreen(() -> ModMenu.getConfigScreen(EcoRewards.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		EditBox reopened = widget(context, EditBox.class, "config.ecorewards.payoutIntervalSeconds", 0);
		assertTrue(context.computeOnClient(client -> reopened.getValue()).equals("5"), "the screen shows the saved value");
		context.runOnClient(client -> reopened.setValue("30"));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(ConfirmScreen.class);
		context.clickScreenButton("config.ecorewards.ui.discard.yes");
		context.waitForScreen(null);
		context.waitTicks(5);
		assertTrue(server.computeOnServer(s -> RewardConfig.get().payoutIntervalSeconds) == 5, "discarded changes are not saved");

		server.runOnServer(s -> RewardConfig.update(RewardConfig.defaults()));
	}

	private static String readConfigFile() {
		try {
			return Files.readString(FabricLoader.getInstance().getConfigDir().resolve(EcoRewards.MOD_ID + ".json"));
		} catch (IOException e) {
			throw new AssertionError("config file not readable", e);
		}
	}

	/**
	 * The {@code index}-th widget of the current screen in screen order (lists included) whose message has the
	 * translation key, or of any message when {@code translationKey} is null.
	 */
	private static <T extends AbstractWidget> T widget(ClientGameTestContext context, Class<T> type, String translationKey, int index) {
		return context.computeOnClient(client -> {
			List<T> found = new ArrayList<>();
			collect(client.screen.children(), type, translationKey, found);
			if (found.size() <= index) {
				throw new AssertionError("no " + type.getSimpleName() + " #" + index + " with message " + translationKey);
			}
			return found.get(index);
		});
	}

	private static <T extends AbstractWidget> void collect(List<? extends GuiEventListener> children, Class<T> type, String translationKey, List<T> found) {
		for (GuiEventListener child : children) {
			if (type.isInstance(child) && (translationKey == null
				|| ((AbstractWidget) child).getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(translationKey))) {
				found.add(type.cast(child));
			}
			if (child instanceof ContainerEventHandler container) {
				collect(container.children(), type, translationKey, found);
			}
		}
	}

	/**
	 * Scrolls the config list down until the widget is fully visible. Rows only get their position when they are drawn,
	 * so a row below the visible part still reports its old position until it scrolls into view.
	 */
	private static void scrollTo(ClientGameTestContext context, AbstractWidget widget) {
		double[] center = context.computeOnClient(client -> new double[] {client.getWindow().getScreenWidth() / 2.0, client.getWindow().getScreenHeight() / 2.0});
		context.getInput().setCursorPos(center[0], center[1]);
		for (int i = 0; i < 80 && !context.computeOnClient(client -> widget.getY() >= 40 && widget.getBottom() <= client.getWindow().getGuiScaledHeight() - 40); i++) {
			context.getInput().scroll(-1);
			context.waitTicks(1);
		}
	}

	private static void hoverWidget(ClientGameTestContext context, AbstractWidget widget) {
		double[] cursor = context.computeOnClient(client -> {
			double scale = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(3);
	}

	private static void clickWidget(ClientGameTestContext context, AbstractWidget widget) {
		hoverWidget(context, widget);
		context.getInput().pressMouse(0);
		context.waitTicks(3);
	}

	/** Removes toasts and chat lines (e.g. the "[Server: ...]" echo of the setup commands) before a screenshot. */
	private static void clearHud(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.getToastManager().clear();
			client.gui.getChat().clearMessages(false);
		});
	}

	private static void screenshot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.getToastManager().clear());
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static String playerName(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getGameProfile().name());
	}

	/** EcoBal balance once the payout went through (the interval is 0 in this test). */
	private static void assertMoney(TestServerContext server, double expected, String when) {
		double actual = server.computeOnServer(s -> EconomyManager.getPlayerBalance(player(s).getUUID()));
		assertTrue(Math.abs(actual - expected) < 0.001, "balance " + when + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
