package codes.nasty.ecorewards;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts of a server-side mod. Players usually don't have the mod, so their game cannot translate its keys:
 * every text is sent as a translatable component whose fallback is the server's language (config option
 * {@code language}). Players who have the mod installed see their own game language, everybody else the server's.
 * The texts come from the language files in the mod jar, the same ones the client uses.
 */
public final class ServerLang {
	/** Languages that ship with the mod; the first one is the default and the fallback for missing keys. */
	public static final List<String> LANGUAGES = List.of("en_us", "de_de");

	private static final Gson GSON = new Gson();
	private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();
	private static volatile String selected = LANGUAGES.getFirst();

	private ServerLang() {
	}

	/** The given code if the mod ships that language, otherwise the default. */
	public static String supported(String code) {
		return code != null && LANGUAGES.contains(code) ? code : LANGUAGES.getFirst();
	}

	/** Sets the server language, see {@link RewardConfig#language}. */
	public static void select(String code) {
		selected = supported(code);
	}

	/** A text in the server language, English if that language lacks it, the key itself if nobody has it. */
	public static String get(String key) {
		return text(selected, key);
	}

	/** A text in the given language, English if that language lacks it, the key itself if nobody has it. */
	public static String text(String code, String key) {
		String text = file(supported(code)).get(key);
		if (text == null) {
			text = file(LANGUAGES.getFirst()).get(key);
		}
		return text != null ? text : key;
	}

	/** A message for players and command senders: their own language with the mod installed, the server's without. */
	public static MutableComponent translatable(String key, Object... args) {
		return Component.translatableWithFallback(key, get(key), args);
	}

	/**
	 * The message template to use for a config text: missing texts and texts that are still the default of another
	 * language become the default of {@code code}, so unchanged messages follow the language setting and edited ones stay.
	 */
	public static String template(String code, String key, String value) {
		if (value == null) {
			return text(code, key);
		}
		for (String other : LANGUAGES) {
			if (!other.equals(code) && value.equals(text(other, key))) {
				return text(code, key);
			}
		}
		return value;
	}

	private static Map<String, String> file(String code) {
		return CACHE.computeIfAbsent(code, ServerLang::load);
	}

	private static Map<String, String> load(String code) {
		String file = "assets/" + EcoRewards.MOD_ID + "/lang/" + code + ".json";
		Path path = FabricLoader.getInstance().getModContainer(EcoRewards.MOD_ID).flatMap(mod -> mod.findPath(file)).orElse(null);
		if (path == null) {
			EcoRewards.LOGGER.error("Language file {} is missing", file);
			return Map.of();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Map<String, String> texts = GSON.fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
			return texts != null ? Map.copyOf(texts) : Map.of();
		} catch (IOException | JsonParseException e) {
			EcoRewards.LOGGER.error("Could not read language file {}", file, e);
			return Map.of();
		}
	}
}
