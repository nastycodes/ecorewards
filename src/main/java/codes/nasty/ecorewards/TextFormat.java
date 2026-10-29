package codes.nasty.ecorewards;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Builds chat components from config texts with {@code &} color codes, {@code &#RRGGBB} colors and {placeholders}. */
public final class TextFormat {
	private TextFormat() {
	}

	public static String fill(String template, Map<String, String> placeholders) {
		String result = template;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			result = result.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return result;
	}

	public static MutableComponent parse(String text) {
		MutableComponent result = Component.empty();
		StringBuilder segment = new StringBuilder();
		Style style = Style.EMPTY.withItalic(false);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != '&' || i + 1 >= text.length()) {
				segment.append(c);
				continue;
			}
			Style next = null;
			int skip = 1;
			char code = text.charAt(i + 1);
			if (code == '#' && i + 8 <= text.length()) {
				TextColor color = TextColor.parseColor(text.substring(i + 1, i + 8)).result().orElse(null);
				if (color != null) {
					next = Style.EMPTY.withItalic(false).withColor(color);
					skip = 7;
				}
			} else {
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				if (formatting == ChatFormatting.RESET) {
					next = Style.EMPTY.withItalic(false);
				} else if (formatting != null && formatting.isColor()) {
					// Like vanilla's legacy codes, a color resets bold/italic/... set before it.
					next = Style.EMPTY.withItalic(false).withColor(formatting);
				} else if (formatting != null) {
					next = style.applyFormat(formatting);
				}
			}
			if (next == null) {
				segment.append(c);
				continue;
			}
			if (!segment.isEmpty()) {
				result.append(Component.literal(segment.toString()).withStyle(style));
				segment.setLength(0);
			}
			style = next;
			i += skip;
		}
		if (!segment.isEmpty()) {
			result.append(Component.literal(segment.toString()).withStyle(style));
		}
		return result;
	}
}
