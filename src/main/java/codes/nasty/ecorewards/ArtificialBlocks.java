package codes.nasty.ecorewards;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Remembers which blocks did not generate with the world, so they can't be farmed for money.
 *
 * <p>Every chunk stores the positions of its artificial blocks. A block becomes artificial when it appears
 * in an empty or fluid-filled spot while the world is running (players, pistons, falling sand, lava meeting
 * water, freezing, mods, ...), unless it is listed as natural growth (trees, sugar cane, ...). When a block
 * merely turns into another one (stripping a log, hoeing dirt, copper oxidizing) it keeps its status, and
 * breaking it clears the position again. Changes made by commands always count as natural.
 */
public final class ArtificialBlocks {
	private static final Codec<LongOpenHashSet> POSITIONS_CODEC = Codec.LONG_STREAM.xmap(
		stream -> new LongOpenHashSet(stream.toArray()),
		set -> Arrays.stream(set.toLongArray())
	);

	public static final AttachmentType<LongOpenHashSet> POSITIONS = AttachmentRegistry.create(
		EcoRewards.id("artificial_blocks"),
		builder -> builder.persistent(POSITIONS_CODEC)
	);

	/** Nesting depth of commands currently executing on the server thread. */
	private static int commandDepth;

	private ArtificialBlocks() {
	}

	public static boolean isArtificial(Level level, BlockPos pos) {
		LongOpenHashSet set = level.getChunkAt(pos).getAttached(POSITIONS);
		return set != null && set.contains(pos.asLong());
	}

	public static void mark(Level level, BlockPos pos) {
		if (!level.isClientSide()) mark(level.getChunkAt(pos), pos);
	}

	public static void unmark(Level level, BlockPos pos) {
		if (!level.isClientSide()) unmark(level.getChunkAt(pos), pos);
	}

	/** Marks every block in the box as natural again and returns how many were artificial. */
	public static int unmarkArea(Level level, BoundingBox box) {
		int removed = 0;
		for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
			for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				LongOpenHashSet set = chunk.getAttached(POSITIONS);
				if (set == null) continue;
				int before = set.size();
				set.removeIf((long packed) -> box.isInside(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed)));
				if (set.size() != before) {
					removed += before - set.size();
					if (set.isEmpty()) chunk.removeAttached(POSITIONS);
					chunk.markUnsaved();
				}
			}
		}
		return removed;
	}

	/** Called by {@link codes.nasty.ecorewards.mixin.LevelChunkMixin} for every block change on the server. */
	public static void onBlockChanged(LevelChunk chunk, BlockPos pos, BlockState oldState, BlockState newState) {
		if (newState.isAir() || newState.getBlock() instanceof LiquidBlock) {
			unmark(chunk, pos);
		} else if (commandDepth > 0) {
			unmark(chunk, pos);
		} else if (!oldState.is(newState.getBlock()) && (oldState.isAir() || oldState.getBlock() instanceof LiquidBlock)) {
			if (RewardTable.isNaturalGrowth(newState.getBlock())) {
				unmark(chunk, pos);
			} else {
				mark(chunk, pos);
			}
		}
	}

	public static void enterCommand() {
		commandDepth++;
	}

	public static void exitCommand() {
		commandDepth--;
	}

	private static void mark(LevelChunk chunk, BlockPos pos) {
		LongOpenHashSet set = chunk.getAttached(POSITIONS);
		if (set == null) {
			set = new LongOpenHashSet();
			chunk.setAttached(POSITIONS, set);
		}
		if (set.add(pos.asLong())) chunk.markUnsaved();
	}

	private static void unmark(LevelChunk chunk, BlockPos pos) {
		LongOpenHashSet set = chunk.getAttached(POSITIONS);
		if (set != null && set.remove(pos.asLong())) {
			if (set.isEmpty()) chunk.removeAttached(POSITIONS);
			chunk.markUnsaved();
		}
	}
}
