package codes.nasty.ecorewards.client.config;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FormattedCharSequence;

/**
 * A settings screen in the style of the vanilla options: one row per option with its name on the left, the control
 * on the right and a reset button behind it. All changes go to a copy of the config; "Done" hands that copy to the mod
 * (on the server thread when a world is open), "Cancel" or Esc throw it away. Lists and maps are edited as text, one
 * entry per line, see {@link TextListScreen}.
 * <p>
 * Texts come from the mod's language files: {@code config.<modid>.<option>} is the label of an option,
 * {@code config.<modid>.<option>.tooltip} its description, {@code config.<modid>.<option>.hint} the line format shown
 * above a list or map editor (optional), {@code config.<modid>.section.<name>} a section header and
 * {@code config.<modid>.ui.*} the texts of the screen itself.
 *
 * @param <C> the mod's config class
 */
public final class ConfigScreen<C> extends Screen {
	/** Accepts namespaced ids such as {@code minecraft:zombie}. */
	public static final Predicate<String> ID = entry -> Identifier.tryParse(entry) != null;
	/** Accepts ids and tags such as {@code #minecraft:logs}. */
	public static final Predicate<String> ID_OR_TAG = entry -> ID.test(entry.startsWith("#") ? entry.substring(1) : entry);

	private static final Gson GSON = new Gson();
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_WIDTH = 380;
	private static final int CONTROL_WIDTH = 130;
	private static final int RESET_WIDTH = 50;
	private static final int GAP = 4;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int ERROR_COLOR = 0xFFFF5555;
	private static final int NOTICE_COLOR = 0xFFFFDD55;

	private final String modId;
	private final Screen parent;
	private final C working;
	private final C defaults;
	private final Consumer<C> onSave;
	private final String initialJson;
	private final List<Row> rows = new ArrayList<>();
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private OptionList list;
	private Button doneButton;

	/**
	 * @param working a copy of the active config that the screen may change
	 * @param defaults a fresh config with the default values, for the reset buttons
	 * @param onSave activates and saves the changed config; runs on the server thread while a world is open
	 */
	public ConfigScreen(String modId, Screen parent, C working, C defaults, Consumer<C> onSave) {
		super(Component.translatable("config." + modId + ".title"));
		this.modId = modId;
		this.parent = parent;
		this.working = working;
		this.defaults = defaults;
		this.onSave = onSave;
		this.initialJson = GSON.toJson(working);
	}

	public ConfigScreen<C> section(String name) {
		this.rows.add(new SectionRow(Component.translatable("config." + this.modId + ".section." + name)));
		return this;
	}

	public ConfigScreen<C> toggle(String key, Function<C, Boolean> getter, BiConsumer<C, Boolean> setter) {
		this.rows.add(new ToggleRow(key, getter, setter));
		return this;
	}

	/** A fixed set of values, shown with {@code namer} and switched through with a click. */
	public <T> ConfigScreen<C> choice(String key, List<T> values, Function<T, Component> namer, Function<C, T> getter, BiConsumer<C, T> setter) {
		this.rows.add(new ChoiceRow<>(key, values, namer, getter, setter));
		return this;
	}

	public ConfigScreen<C> integer(String key, int min, int max, Function<C, Integer> getter, BiConsumer<C, Integer> setter) {
		this.rows.add(new TextRow<>(key, getter, setter, 12, String::valueOf, text -> {
			try {
				int value = Integer.parseInt(text.trim());
				return value >= min && value <= max ? value : null;
			} catch (NumberFormatException e) {
				return null;
			}
		}, range(String.valueOf(min), String.valueOf(max))));
		return this;
	}

	public ConfigScreen<C> decimal(String key, double min, double max, Function<C, Double> getter, BiConsumer<C, Double> setter) {
		return this.decimal(key, min, max, 1.0, getter, setter);
	}

	/** A value between 0 and 1 that players see and type as a percentage (0.95 = 95). */
	public ConfigScreen<C> percent(String key, Function<C, Double> getter, BiConsumer<C, Double> setter) {
		return this.decimal(key, 0.0, 1.0, 100.0, getter, setter);
	}

	private ConfigScreen<C> decimal(String key, double min, double max, double scale, Function<C, Double> getter, BiConsumer<C, Double> setter) {
		this.rows.add(new TextRow<>(key, getter, setter, 24, value -> formatDecimal(value, scale), text -> {
			Double value = parseDecimal(text);
			if (value == null) return null;
			value /= scale;
			return value >= min && value <= max ? value : null;
		}, range(formatDecimal(min, scale), formatDecimal(max, scale))));
		return this;
	}

	public ConfigScreen<C> text(String key, int maxLength, Function<C, String> getter, BiConsumer<C, String> setter) {
		this.rows.add(new TextRow<>(key, getter, setter, maxLength, Function.identity(), text -> text, null));
		return this;
	}

	/** A list of strings, edited one entry per line; {@code validEntry} decides which lines are accepted. */
	public ConfigScreen<C> list(String key, Predicate<String> validEntry, Function<C, List<String>> getter, BiConsumer<C, List<String>> setter) {
		this.rows.add(new LinesRow<>(key, getter, setter, false, List::size, value -> value, lines -> {
			List<String> result = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.isEmpty()) continue;
				if (!validEntry.test(line)) throw this.lineError(i, "bad_entry", line);
				if (result.contains(line)) throw this.lineError(i, "duplicate", line);
				result.add(line);
			}
			return result;
		}));
		return this;
	}

	/**
	 * A map, edited as {@code key = value} lines. {@code valueParser} returns null for values it does not accept;
	 * the setter gets a {@link LinkedHashMap} in the order of the lines.
	 */
	public <V> ConfigScreen<C> map(String key, Predicate<String> validKey, Function<String, V> valueParser, Function<V, String> valueFormatter,
		Function<C, Map<String, V>> getter, BiConsumer<C, Map<String, V>> setter) {
		this.rows.add(new LinesRow<>(key, getter, setter, true, Map::size, value -> {
			List<String> lines = new ArrayList<>();
			value.forEach((entry, entryValue) -> lines.add(entry + " = " + valueFormatter.apply(entryValue)));
			return lines;
		}, lines -> {
			Map<String, V> result = new LinkedHashMap<>();
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.isEmpty()) continue;
				int separator = line.indexOf('=');
				if (separator < 0) throw this.lineError(i, "missing_separator", line);
				String entry = line.substring(0, separator).trim();
				String text = line.substring(separator + 1).trim();
				if (!validKey.test(entry)) throw this.lineError(i, "bad_entry", entry);
				if (result.containsKey(entry)) throw this.lineError(i, "duplicate", entry);
				V parsed = valueParser.apply(text);
				if (parsed == null) throw this.lineError(i, "bad_value", text);
				result.put(entry, parsed);
			}
			return result;
		}));
		return this;
	}

	/** Parses a number typed by a player; a comma works as decimal separator too. Null if it is not a finite number. */
	public static Double parseDecimal(String text) {
		try {
			double value = Double.parseDouble(text.trim().replace(',', '.'));
			return Double.isFinite(value) ? value : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Shortest plain form of a number: 6.0 becomes "6", 0.05 stays "0.05", never "1.0E-4". */
	public static String formatDecimal(double value) {
		return formatDecimal(value, 1.0);
	}

	/** {@link #formatDecimal(double)} of value × scale, multiplied exactly: 0.07 × 100 is "7", not "7.000000000000001". */
	public static String formatDecimal(double value, double scale) {
		return new BigDecimal(Double.toString(value)).multiply(new BigDecimal(Double.toString(scale))).stripTrailingZeros().toPlainString();
	}

	private Component range(String min, String max) {
		return Component.translatable(this.ui("range"), min, max);
	}

	private IllegalArgumentException lineError(int index, String problem, String text) {
		return new IllegalArgumentException(I18n.get(this.ui("line"), index + 1, I18n.get(this.ui(problem), text)));
	}

	private String ui(String name) {
		return "config." + this.modId + ".ui." + name;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);
		this.list = this.layout.addToContents(new OptionList());
		if (this.minecraft.level != null && this.minecraft.getSingleplayerServer() == null) {
			this.list.add(new NoticeRow(Component.translatable(this.ui("remote"))));
		}
		this.rows.forEach(this.list::add);

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(150).build());
		this.doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.save()).width(150).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.updateDoneButton();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}
	}

	/** Esc and Cancel: leave without saving, after asking when something was changed. */
	@Override
	public void onClose() {
		if (GSON.toJson(this.working).equals(this.initialJson)) {
			this.minecraft.setScreen(this.parent);
			return;
		}
		this.minecraft.setScreen(new ConfirmScreen(
			discard -> this.minecraft.setScreen(discard ? this.parent : this),
			Component.translatable(this.ui("discard.title")),
			Component.translatable(this.ui("discard.message")),
			Component.translatable(this.ui("discard.yes")),
			CommonComponents.GUI_BACK));
	}

	private void save() {
		if (!this.allValid()) return;
		C result = this.working;
		MinecraftServer server = this.minecraft.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> this.onSave.accept(result));
		} else {
			this.onSave.accept(result);
		}
		this.minecraft.setScreen(this.parent);
	}

	private boolean allValid() {
		for (Row row : this.rows) {
			if (!row.isValid()) return false;
		}
		return true;
	}

	private void updateDoneButton() {
		if (this.doneButton == null) return;
		boolean valid = this.allValid();
		this.doneButton.active = valid;
		this.doneButton.setTooltip(valid ? null : Tooltip.create(Component.translatable(this.ui("invalid"))));
	}

	private final class OptionList extends ContainerObjectSelectionList<Row> {
		OptionList() {
			super(ConfigScreen.this.minecraft, ConfigScreen.this.width, ConfigScreen.this.layout.getContentHeight(), ConfigScreen.this.layout.getHeaderHeight(), ROW_HEIGHT);
		}

		void add(Row row) {
			this.addEntry(row, row.rowHeight());
		}

		@Override
		public int getRowWidth() {
			return Math.min(ROW_WIDTH, this.width - 24);
		}
	}

	private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
		int rowHeight() {
			return ROW_HEIGHT;
		}

		boolean isValid() {
			return true;
		}
	}

	/** Static text, only for narration. */
	private static NarratableEntry narratable(Component text) {
		return new NarratableEntry() {
			@Override
			public NarratableEntry.NarrationPriority narrationPriority() {
				return NarratableEntry.NarrationPriority.HOVERED;
			}

			@Override
			public void updateNarration(NarrationElementOutput output) {
				output.add(NarratedElementType.TITLE, text);
			}
		};
	}

	private final class SectionRow extends Row {
		private final Component text;

		SectionRow(Component text) {
			this.text = text.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
		}

		@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			graphics.drawCenteredString(ConfigScreen.this.font, this.text, this.getContentXMiddle(), this.getContentY() + this.getContentHeight() - 10, TEXT_COLOR);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(narratable(this.text));
		}
	}

	/** A highlighted hint over the full width, wrapped onto as many lines as it needs. */
	private final class NoticeRow extends Row {
		private final Component text;
		private final List<FormattedCharSequence> lines;

		NoticeRow(Component text) {
			this.text = text;
			this.lines = ConfigScreen.this.font.split(text, ROW_WIDTH - 8);
		}

		@Override
		int rowHeight() {
			return this.lines.size() * 10 + 8;
		}

		@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int y = this.getContentY() + 2;
			for (FormattedCharSequence line : this.lines) {
				graphics.drawCenteredString(ConfigScreen.this.font, line, this.getContentXMiddle(), y, NOTICE_COLOR);
				y += 10;
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(narratable(this.text));
		}
	}

	/** Label, control and reset button of one config value. */
	private abstract class OptionRow<T> extends Row {
		final Component label;
		final Function<C, T> getter;
		final BiConsumer<C, T> setter;
		final Button resetButton;
		final String translationKey;
		private final Component extraInfo;
		/** Built on first use: {@link #describe} needs the fields of the subclass. */
		private List<FormattedCharSequence> tooltip;
		boolean valid = true;

		OptionRow(String key, Function<C, T> getter, BiConsumer<C, T> setter, Component extraInfo) {
			this.translationKey = "config." + ConfigScreen.this.modId + "." + key;
			this.label = Component.translatable(this.translationKey);
			this.getter = getter;
			this.setter = setter;
			this.extraInfo = extraInfo;
			this.resetButton = Button.builder(Component.translatable(ConfigScreen.this.ui("reset_button")), button -> this.reset()).size(RESET_WIDTH, 20).build();
		}

		private List<FormattedCharSequence> tooltip() {
			if (this.tooltip == null) {
				Component defaultText = this.describe(this.getter.apply(ConfigScreen.this.defaults));
				MutableComponent description = Component.empty().append(this.label.copy().withStyle(ChatFormatting.YELLOW));
				if (I18n.exists(this.translationKey + ".tooltip")) {
					description.append("\n").append(Component.translatable(this.translationKey + ".tooltip"));
				}
				if (this.extraInfo != null) {
					description.append("\n").append(this.extraInfo.copy().withStyle(ChatFormatting.GRAY));
				}
				description.append("\n").append(Component.translatable(ConfigScreen.this.ui("default"), defaultText).withStyle(ChatFormatting.GRAY));
				this.tooltip = Tooltip.splitTooltip(ConfigScreen.this.minecraft, description);
				this.resetButton.setTooltip(Tooltip.create(Component.translatable(ConfigScreen.this.ui("reset"), defaultText)));
			}
			return this.tooltip;
		}

		abstract AbstractWidget control();

		@Override
		boolean isValid() {
			return this.valid;
		}

		/** Puts a value into the control, e.g. after a reset. */
		abstract void show(T value);

		/** The value as players see it, for the tooltips. */
		abstract Component describe(T value);

		/** Copies a default value, so later changes to the working copy never reach the defaults. */
		T copyOf(T value) {
			return value;
		}

		void set(T value) {
			this.valid = true;
			this.setter.accept(ConfigScreen.this.working, value);
			this.changed();
		}

		void changed() {
			this.resetButton.active = !this.valid || !Objects.equals(this.getter.apply(ConfigScreen.this.working), this.getter.apply(ConfigScreen.this.defaults));
			ConfigScreen.this.updateDoneButton();
		}

		private void reset() {
			T value = this.copyOf(this.getter.apply(ConfigScreen.this.defaults));
			this.show(value);
			this.set(value);
		}

		@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			List<FormattedCharSequence> tooltip = this.tooltip();
			int x = this.getContentX();
			int y = this.getContentY();
			int resetX = this.getContentRight() - RESET_WIDTH;
			int controlX = resetX - GAP - CONTROL_WIDTH;
			int labelWidth = controlX - GAP - x;
			// StringWidget.clipText always adds "...", so only clip labels that are really too long.
			FormattedCharSequence text = ConfigScreen.this.font.width(this.label) <= labelWidth
				? this.label.getVisualOrderText()
				: StringWidget.clipText(this.label, ConfigScreen.this.font, labelWidth);
			graphics.drawString(ConfigScreen.this.font, text, x, y + (this.getContentHeight() - 8) / 2, this.valid ? TEXT_COLOR : ERROR_COLOR);

			AbstractWidget control = this.control();
			control.setPosition(controlX, y);
			control.render(graphics, mouseX, mouseY, partialTick);
			this.resetButton.setPosition(resetX, y);
			this.resetButton.render(graphics, mouseX, mouseY, partialTick);

			boolean overLabel = mouseX >= x && mouseX < controlX && mouseY >= y && mouseY < y + this.getContentHeight();
			if (overLabel || control.isHovered()) {
				graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.control(), this.resetButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.control(), this.resetButton);
		}
	}

	private final class ToggleRow extends OptionRow<Boolean> {
		private final CycleButton<Boolean> button;

		ToggleRow(String key, Function<C, Boolean> getter, BiConsumer<C, Boolean> setter) {
			super(key, getter, setter, null);
			this.button = CycleButton.onOffBuilder(getter.apply(ConfigScreen.this.working))
				.displayOnlyValue()
				.create(0, 0, CONTROL_WIDTH, 20, this.label, (button, value) -> this.set(value));
			this.changed();
		}

		@Override
		AbstractWidget control() {
			return this.button;
		}

		@Override
		void show(Boolean value) {
			this.button.setValue(value);
		}

		@Override
		Component describe(Boolean value) {
			return CommonComponents.optionStatus(value);
		}
	}

	private final class ChoiceRow<T> extends OptionRow<T> {
		private final Function<T, Component> namer;
		private final CycleButton<T> button;

		ChoiceRow(String key, List<T> values, Function<T, Component> namer, Function<C, T> getter, BiConsumer<C, T> setter) {
			super(key, getter, setter, null);
			this.namer = namer;
			this.button = CycleButton.builder(namer, getter.apply(ConfigScreen.this.working))
				.withValues(values)
				.displayOnlyValue()
				.create(0, 0, CONTROL_WIDTH, 20, this.label, (button, value) -> this.set(value));
			this.changed();
		}

		@Override
		AbstractWidget control() {
			return this.button;
		}

		@Override
		void show(T value) {
			this.button.setValue(value);
		}

		@Override
		Component describe(T value) {
			return this.namer.apply(value);
		}
	}

	/** Numbers and texts: a text field that turns red while the input is not accepted. */
	private final class TextRow<T> extends OptionRow<T> {
		private final Function<T, String> formatter;
		private final EditBox box;

		TextRow(String key, Function<C, T> getter, BiConsumer<C, T> setter, int maxLength, Function<T, String> formatter, Function<String, T> parser, Component extraInfo) {
			super(key, getter, setter, extraInfo);
			this.formatter = formatter;
			this.box = new EditBox(ConfigScreen.this.font, 0, 0, CONTROL_WIDTH, 20, this.label);
			this.box.setMaxLength(maxLength);
			this.box.setValue(formatter.apply(getter.apply(ConfigScreen.this.working)));
			this.box.moveCursorToStart(false);
			this.box.setResponder(text -> {
				T value = parser.apply(text);
				if (value != null) {
					this.set(value);
				} else {
					this.valid = false;
					this.changed();
				}
				this.box.setTextColor(this.valid ? EditBox.DEFAULT_TEXT_COLOR : ERROR_COLOR);
			});
			this.changed();
		}

		@Override
		AbstractWidget control() {
			return this.box;
		}

		@Override
		void show(T value) {
			this.box.setValue(this.formatter.apply(value));
			this.box.moveCursorToStart(false);
		}

		@Override
		Component describe(T value) {
			String text = this.formatter.apply(value);
			return Component.literal(text.isEmpty() ? "\"\"" : text);
		}
	}

	/** Lists and maps: a button that opens a text editor with one entry per line. */
	private final class LinesRow<T> extends OptionRow<T> {
		private final Function<T, Integer> counter;
		private final Function<T, List<String>> toLines;
		private final Function<List<String>, T> fromLines;
		private final Button button;

		LinesRow(String key, Function<C, T> getter, BiConsumer<C, T> setter, boolean map, Function<T, Integer> counter,
			Function<T, List<String>> toLines, Function<List<String>, T> fromLines) {
			super(key, getter, setter, null);
			this.counter = counter;
			this.toLines = toLines;
			this.fromLines = fromLines;
			// An option can explain its line format with a ".hint" text, e.g. "id or #tag = money, xp".
			Component hint = I18n.exists(this.translationKey + ".hint")
				? Component.translatable(this.translationKey + ".hint")
				: Component.translatable(ConfigScreen.this.ui(map ? "map_hint" : "list_hint"));
			this.button = Button.builder(Component.empty(), button -> ConfigScreen.this.minecraft.setScreen(new TextListScreen(
				ConfigScreen.this, this.label, hint, String.join("\n", toLines.apply(getter.apply(ConfigScreen.this.working))),
				text -> {
					try {
						fromLines.apply(text.lines().map(String::trim).toList());
						return null;
					} catch (IllegalArgumentException e) {
						return Component.literal(e.getMessage());
					}
				},
				text -> this.set(fromLines.apply(text.lines().map(String::trim).toList())))))
				.size(CONTROL_WIDTH, 20)
				.build();
			this.changed();
		}

		@Override
		AbstractWidget control() {
			return this.button;
		}

		@Override
		void show(T value) {
		}

		@Override
		T copyOf(T value) {
			return this.fromLines.apply(this.toLines.apply(value));
		}

		@Override
		void changed() {
			super.changed();
			this.button.setMessage(Component.translatable(ConfigScreen.this.ui("edit"), this.counter.apply(this.getter.apply(ConfigScreen.this.working))));
		}

		@Override
		Component describe(T value) {
			return Component.translatable(ConfigScreen.this.ui("entries"), this.counter.apply(value));
		}
	}
}
