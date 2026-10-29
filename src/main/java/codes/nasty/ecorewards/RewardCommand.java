package codes.nasty.ecorewards;

import codes.nasty.ecorewards.Progression.Progress;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * {@code /ecorewards} (alias {@code /lvl}): players see their own level, operators manage levels,
 * reload the config and mark admin-built areas as natural.
 */
public final class RewardCommand {
	/** Largest area {@code /ecorewards natural} accepts, in chunks. */
	private static final int MAX_NATURAL_CHUNKS = 256;

	private RewardCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal(EcoRewards.MOD_ID)
			.executes(context -> showInfo(context.getSource(), context.getSource().getPlayerOrException(), false))
			.then(Commands.literal("info")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> showInfo(context.getSource(), EntityArgument.getPlayer(context, "player"), true))))
			.then(Commands.literal("setlevel")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("level", IntegerArgumentType.integer(1))
						.executes(RewardCommand::setLevel))))
			.then(Commands.literal("addxp")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("xp", IntegerArgumentType.integer(1))
						.executes(RewardCommand::addXp))))
			.then(Commands.literal("reset")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(RewardCommand::reset)))
			.then(Commands.literal("check")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("pos", BlockPosArgument.blockPos())
					.executes(RewardCommand::check)))
			.then(Commands.literal("natural")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("from", BlockPosArgument.blockPos())
					.then(Commands.argument("to", BlockPosArgument.blockPos())
						.executes(RewardCommand::markNatural))))
			.then(Commands.literal("reload")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(RewardCommand::reload)));
		dispatcher.register(Commands.literal("lvl").executes(root.getCommand()).redirect(root));
	}

	private static int showInfo(CommandSourceStack source, ServerPlayer player, boolean other) {
		RewardConfig.MessageSettings messages = RewardConfig.get().messages;
		String template = other ? messages.infoOther : messages.info;
		Progress progress = Progression.get(player);
		source.sendSuccess(() -> TextFormat.parse(TextFormat.fill(template, RewardHandler.placeholders(player, progress))), false);
		return progress.level();
	}

	private static int setLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		int level = Math.min(IntegerArgumentType.getInteger(context, "level"), RewardConfig.get().levels.maxLevel);
		Progression.set(player, new Progress(level, 0));
		context.getSource().sendSuccess(() -> ServerLang.translatable("ecorewards.command.setlevel", player.getGameProfile().name(), level), true);
		return level;
	}

	private static int addXp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		int xp = IntegerArgumentType.getInteger(context, "xp");
		Progress before = Progression.get(player);
		Progress after = Progression.addXp(before, xp);
		Progression.set(player, after);
		if (after.level() > before.level()) RewardHandler.onLevelUp(player, after);
		context.getSource().sendSuccess(() -> ServerLang.translatable("ecorewards.command.addxp", xp, player.getGameProfile().name(), after.level()), true);
		return after.level();
	}

	private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		Progression.set(player, Progress.START);
		context.getSource().sendSuccess(() -> ServerLang.translatable("ecorewards.command.reset", player.getGameProfile().name()), true);
		return 1;
	}

	private static int check(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
		boolean artificial = ArtificialBlocks.isArtificial(context.getSource().getLevel(), pos);
		Component block = context.getSource().getLevel().getBlockState(pos).getBlock().getName();
		String key = artificial ? "ecorewards.command.check.artificial" : "ecorewards.command.check.natural";
		context.getSource().sendSuccess(() -> ServerLang.translatable(key, block, pos.toShortString()), false);
		return artificial ? 1 : 0;
	}

	private static int markNatural(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		BoundingBox box = BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to"));
		int chunks = ((box.maxX() >> 4) - (box.minX() >> 4) + 1) * ((box.maxZ() >> 4) - (box.minZ() >> 4) + 1);
		if (chunks > MAX_NATURAL_CHUNKS) {
			context.getSource().sendFailure(ServerLang.translatable("ecorewards.command.natural.too_large", chunks, MAX_NATURAL_CHUNKS));
			return 0;
		}
		int count = ArtificialBlocks.unmarkArea(context.getSource().getLevel(), box);
		context.getSource().sendSuccess(() -> ServerLang.translatable("ecorewards.command.natural", count), true);
		return count;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		try {
			RewardConfig.reload();
			RewardTable.reportUnknownIds(RewardConfig.get());
		} catch (Exception e) {
			context.getSource().sendFailure(ServerLang.translatable("ecorewards.command.reload_failed", e.getMessage()));
			return 0;
		}
		context.getSource().sendSuccess(() -> ServerLang.translatable("ecorewards.command.reloaded"), true);
		return 1;
	}
}
