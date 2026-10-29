package codes.nasty.ecorewards.mixin;

import codes.nasty.ecorewards.ArtificialBlocks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reports every block change in a loaded chunk to {@link ArtificialBlocks}. */
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
	@WrapOperation(
		method = "setBlockState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;")
	)
	private BlockState ecorewards$trackChange(LevelChunkSection section, int x, int y, int z, BlockState state, Operation<BlockState> original, @Local(argsOnly = true) BlockPos pos) {
		BlockState oldState = original.call(section, x, y, z, state);
		LevelChunk chunk = (LevelChunk) (Object) this;
		if (oldState != state && !chunk.getLevel().isClientSide()) {
			ArtificialBlocks.onBlockChanged(chunk, pos, oldState, state);
		}
		return oldState;
	}
}
