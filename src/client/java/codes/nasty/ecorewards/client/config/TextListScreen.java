package codes.nasty.ecorewards.client.config;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Edits a list or map option of a {@link ConfigScreen} as text, one entry per line.
 * "Done" is only possible while the validator accepts every line; the first problem is shown under the text.
 */
final class TextListScreen extends Screen {
	private static final int MAX_WIDTH = 360;
	private static final int TOP = 40;
	private static final int ERROR_COLOR = 0xFFFF5555;
	private static final int HINT_COLOR = 0xFFAAAAAA;

	private final Screen parent;
	private final Component hint;
	private final Function<String, Component> validator;
	private final Consumer<String> onDone;
	private String text;
	private Component error;
	private Button doneButton;

	/**
	 * @param validator returns the problem with the text, or null when it can be saved
	 * @param onDone receives the accepted text
	 */
	TextListScreen(Screen parent, Component title, Component hint, String text, Function<String, Component> validator, Consumer<String> onDone) {
		super(title);
		this.parent = parent;
		this.hint = hint;
		this.text = text;
		this.validator = validator;
		this.onDone = onDone;
	}

	@Override
	protected void init() {
		int width = Math.min(MAX_WIDTH, this.width - 40);
		int left = (this.width - width) / 2;
		MultiLineEditBox editBox = MultiLineEditBox.builder()
			.setX(left)
			.setY(TOP)
			.setShowBackground(true)
			.build(this.font, width, Math.max(40, this.height - TOP - 56), this.title);
		editBox.setCharacterLimit(32_000);
		editBox.setValue(this.text);
		editBox.setValueListener(value -> {
			this.text = value;
			this.validate();
		});
		this.addRenderableWidget(editBox);

		int buttonY = this.height - 28;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
			.bounds(this.width / 2 - 154, buttonY, 150, 20)
			.build());
		this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
			this.onDone.accept(this.text);
			this.minecraft.setScreen(this.parent);
		}).bounds(this.width / 2 + 4, buttonY, 150, 20).build());
		this.validate();
		this.setInitialFocus(editBox);
	}

	private void validate() {
		this.error = this.validator.apply(this.text);
		if (this.doneButton != null) {
			this.doneButton.active = this.error == null;
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		graphics.drawCenteredString(this.font, this.hint, this.width / 2, 26, HINT_COLOR);
		if (this.error != null) {
			graphics.drawCenteredString(this.font, this.error, this.width / 2, this.height - 42, ERROR_COLOR);
		}
	}

	/** Esc and Cancel: back without changes. */
	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
