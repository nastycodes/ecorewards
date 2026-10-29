package codes.nasty.ecorewards.mixin;

import codes.nasty.ecorewards.ArtificialBlocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import org.spongepowered.asm.mixin.Mixin;

/** Blocks set by commands (/setblock, /fill, functions, command blocks) count as natural, e.g. for admin-built mines. */
@Mixin(Commands.class)
abstract class CommandsMixin {
	@WrapMethod(method = "executeCommandInContext")
	private static void ecorewards$trackCommand(CommandSourceStack source, Consumer<ExecutionContext<CommandSourceStack>> consumer, Operation<Void> original) {
		ArtificialBlocks.enterCommand();
		try {
			original.call(source, consumer);
		} finally {
			ArtificialBlocks.exitCommand();
		}
	}
}
