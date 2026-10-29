package codes.nasty.ecorewards.mixin;

import codes.nasty.ecorewards.RewardHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tags mobs from monster spawners (and their riders) so they pay the reduced spawner reward. */
@Mixin(BaseSpawner.class)
abstract class BaseSpawnerMixin {
	@WrapOperation(
		method = "serverTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z")
	)
	private boolean ecorewards$tagSpawnedMob(ServerLevel level, Entity entity, Operation<Boolean> original) {
		entity.getSelfAndPassengers().forEach(spawned -> spawned.addTag(RewardHandler.SPAWNER_TAG));
		return original.call(level, entity);
	}
}
