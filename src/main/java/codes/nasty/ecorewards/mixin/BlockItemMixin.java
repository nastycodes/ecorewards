package codes.nasty.ecorewards.mixin;

import codes.nasty.ecorewards.ArtificialBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Placed blocks are artificial even where the chunk rule would miss them:
 * natural-growth blocks (a placed log) and blocks placed into grass or snow.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void ecorewards$markPlaced(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			ArtificialBlocks.mark(context.getLevel(), context.getClickedPos());
		}
	}
}
