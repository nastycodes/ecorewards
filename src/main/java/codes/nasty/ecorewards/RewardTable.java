package codes.nasty.ecorewards;

import codes.nasty.ecorewards.RewardConfig.Reward;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Turns the id/tag keyed config maps into fast per-block and per-entity lookups.
 * Results are cached; the caches are dropped on config reload and whenever tags are reloaded.
 */
public final class RewardTable {
	private static Lookup<Block, Reward> blockRewards = Lookup.empty(BuiltInRegistries.BLOCK);
	private static Lookup<EntityType<?>, Reward> mobRewards = Lookup.empty(BuiltInRegistries.ENTITY_TYPE);
	private static Lookup<Block, Boolean> naturalGrowth = Lookup.empty(BuiltInRegistries.BLOCK);

	private RewardTable() {
	}

	/** The block's reward and whether it came from the "default" entry, or null if nothing applies. */
	public static @Nullable Match<Reward> block(Block block) {
		return blockRewards.find(block);
	}

	public static @Nullable Match<Reward> mob(EntityType<?> type) {
		return mobRewards.find(type);
	}

	public static boolean isNaturalGrowth(Block block) {
		return naturalGrowth.find(block) != null;
	}

	static void rebuild(RewardConfig config) {
		blockRewards = Lookup.compile(BuiltInRegistries.BLOCK, config.blocks.rewards, "blocks.rewards");
		mobRewards = Lookup.compile(BuiltInRegistries.ENTITY_TYPE, config.mobs.rewards, "mobs.rewards");
		naturalGrowth = Lookup.compile(BuiltInRegistries.BLOCK, naturalGrowthMap(config), "antiExploit.naturalGrowthBlocks");
	}

	/** Logs config ids that match no block or entity. Runs at server start, when every mod has registered its content. */
	public static void reportUnknownIds(RewardConfig config) {
		reportUnknownIds(BuiltInRegistries.BLOCK, config.blocks.rewards, "blocks.rewards");
		reportUnknownIds(BuiltInRegistries.ENTITY_TYPE, config.mobs.rewards, "mobs.rewards");
		reportUnknownIds(BuiltInRegistries.BLOCK, naturalGrowthMap(config), "antiExploit.naturalGrowthBlocks");
	}

	private static void reportUnknownIds(Registry<?> registry, Map<String, ?> entries, String configPath) {
		for (String key : entries.keySet()) {
			Identifier id = Identifier.tryParse(key.trim());
			if (id != null && !key.trim().equals("default") && !registry.containsKey(id)) {
				EcoRewards.LOGGER.warn("Unknown id '{}' in {} (typo or missing mod?)", key, configPath);
			}
		}
	}

	private static Map<String, Boolean> naturalGrowthMap(RewardConfig config) {
		Map<String, Boolean> map = new LinkedHashMap<>();
		config.antiExploit.naturalGrowthBlocks.forEach(key -> map.put(key, Boolean.TRUE));
		return map;
	}

	/** Tags may have changed (data pack reload), so every cached tag match is stale. */
	public static void clearCaches() {
		blockRewards.cache.clear();
		mobRewards.cache.clear();
		naturalGrowth.cache.clear();
	}

	public record Match<V>(V value, boolean fallback) {
	}

	private static final class Lookup<T, V> {
		private final Registry<T> registry;
		private final Map<Identifier, V> byId;
		private final List<Map.Entry<TagKey<T>, V>> byTag;
		private final @Nullable V fallback;
		/** Misses are cached too, as a match with a null value. */
		final Map<T, Match<V>> cache = new ConcurrentHashMap<>();

		private Lookup(Registry<T> registry, Map<Identifier, V> byId, List<Map.Entry<TagKey<T>, V>> byTag, @Nullable V fallback) {
			this.registry = registry;
			this.byId = byId;
			this.byTag = byTag;
			this.fallback = fallback;
		}

		static <T, V> Lookup<T, V> empty(Registry<T> registry) {
			return new Lookup<>(registry, Map.of(), List.of(), null);
		}

		static <T, V> Lookup<T, V> compile(Registry<T> registry, Map<String, V> entries, String configPath) {
			Map<Identifier, V> byId = new HashMap<>();
			List<Map.Entry<TagKey<T>, V>> byTag = new ArrayList<>();
			V fallback = null;
			for (Map.Entry<String, V> entry : entries.entrySet()) {
				String key = entry.getKey().trim();
				if (key.equals("default")) {
					fallback = entry.getValue();
					continue;
				}
				boolean tag = key.startsWith("#");
				Identifier id = Identifier.tryParse(tag ? key.substring(1) : key);
				if (id == null) {
					EcoRewards.LOGGER.warn("Ignoring invalid id '{}' in {}", key, configPath);
				} else if (tag) {
					byTag.add(Map.entry(TagKey.create(registry.key(), id), entry.getValue()));
				} else {
					byId.put(id, entry.getValue());
				}
			}
			return new Lookup<>(registry, byId, byTag, fallback);
		}

		@Nullable Match<V> find(T value) {
			Match<V> match = cache.computeIfAbsent(value, this::resolve);
			return match.value() == null ? null : match;
		}

		private Match<V> resolve(T value) {
			V exact = byId.get(registry.getKey(value));
			if (exact != null) return new Match<>(exact, false);
			Holder<T> holder = registry.wrapAsHolder(value);
			for (Map.Entry<TagKey<T>, V> entry : byTag) {
				if (holder.is(entry.getKey())) return new Match<>(entry.getValue(), false);
			}
			return new Match<>(fallback, true);
		}
	}
}
