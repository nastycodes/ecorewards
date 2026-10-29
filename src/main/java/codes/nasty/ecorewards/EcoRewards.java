package codes.nasty.ecorewards;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EcoRewards implements ModInitializer {
	public static final String MOD_ID = "ecorewards";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Touch the attachment holders so both types are registered before any world or player loads.
		LOGGER.debug("Registered attachments {} and {}", Progression.PROGRESS.identifier(), ArtificialBlocks.POSITIONS.identifier());
		RewardConfig.load();

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			RewardHandler.beforeBlockBreak(level, player, pos, state);
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> RewardHandler.afterBlockBreak(player, pos));
		ServerLivingEntityEvents.AFTER_DEATH.register(RewardHandler::afterDeath);

		ServerTickEvents.END_SERVER_TICK.register(server -> Payouts.tick());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Payouts.flush(handler.getPlayer().getUUID()));
		// EcoBal saves its balances when the server has stopped, so everything still pending has to be in by then.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Payouts.flushAll());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> RewardTable.reportUnknownIds(RewardConfig.get()));
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> RewardTable.clearCaches());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RewardCommand.register(dispatcher));
	}
}
