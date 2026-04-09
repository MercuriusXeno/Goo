package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.item.gasket.ChoralTunerItem;
import com.mercuriusxeno.goo.network.CanisterRenamePayload;
import com.mercuriusxeno.goo.network.CanisterUnlinkPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Naming modal for machines: text input with confirm, cancel, and optional
 * "Sever Link" button with deadman's switch (two clicks to confirm).
 * Opened by the choral tuner when shift-clicking a gasket machine.
 */
public class CanisterNamingScreen extends Screen {

    /** Width of the dialog panel. */
    private static final int PANEL_WIDTH = 200;
    /** Height of the dialog panel (taller when link button is shown). */
    private static final int BASE_PANEL_HEIGHT = 80;
    /** Extra height for the "Sever Link" button row. */
    private static final int LINK_ROW_HEIGHT = 25;
    /** Button dimensions. */
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;

    /** Background color for the sever button in normal state. */
    private static final int SEVER_BG = 0xFF3A1A1A;
    /** Background color for the sever button in hover state. */
    private static final int SEVER_HOVER = 0xFF5A2A2A;
    /** Background color for the sever button in confirm state. */
    private static final int SEVER_CONFIRM_BG = 0xFFAA0000;
    /** Background color for the sever button in confirm hover state. */
    private static final int SEVER_CONFIRM_HOVER = 0xFFCC2222;

    /** Divisor for centering calculations. */
    private static final int HALF = 2;

    /** Horizontal inset for the label input field. */
    private static final int INPUT_INSET = 10;

    /** Vertical offset from panel top to the label input field. */
    private static final int INPUT_TOP_OFFSET = 25;

    /** Input field height in pixels. */
    private static final int INPUT_HEIGHT = 20;

    /** Vertical offset from panel top to the button row. */
    private static final int BUTTON_ROW_OFFSET = 55;

    /** Horizontal gap between adjacent buttons. */
    private static final int BUTTON_GAP = 5;

    /** Confirm button label (check mark). */
    private static final String CONFIRM_LABEL = "\u2713";

    /** Confirm label color (green). */
    private static final int CONFIRM_LABEL_COLOR = 0x55FF55;

    /** Cancel button label (cross). */
    private static final String CANCEL_LABEL = "\u2717";

    /** Cancel label color (red). */
    private static final int CANCEL_LABEL_COLOR = 0xFF5555;

    /** Confirm background color (dark green). */
    private static final int CONFIRM_BG = 0xFF1A3A1A;

    /** Confirm hover color (lighter green). */
    private static final int CONFIRM_HOVER = 0xFF2A5A2A;

    /** Sever link button text for initial state. */
    private static final String SEVER_LINK_TEXT = "Sever Link";

    /** Sever link button text for confirmation prompt. */
    private static final String SEVER_CONFIRM_TEXT = "You sure?";

    /** Semi-transparent black for panel background. */
    private static final int PANEL_BG_COLOR = 0xCC000000;

    /** Title text vertical offset from panel top. */
    private static final int TITLE_TOP_OFFSET = 8;

    /** Fully opaque white for text rendering. */
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    /** GLFW key code for Enter/Return. */
    private static final int KEY_ENTER = 257;

    /** Font line height for centering button text. */
    private static final int FONT_LINE_HEIGHT = 8;

    private final BlockPos canisterPos;
    private final int slot;
    private final String initialLabel;
    private final boolean hasLink;
    private EditBox labelInput;
    private TintedButton severButton;
    private boolean severConfirmPending;

    /**
     * Creates the naming screen for the machine at the given position and slot.
     *
     * @param pos the block position
     * @param slot the slot index
     * @param currentLabel the current label text
     * @param hasLink whether the machine has a gasket link
     */
    public CanisterNamingScreen(BlockPos pos, int slot, String currentLabel,
            boolean hasLink) {
        super(Component.translatable("goo.tuner.naming_header"));
        this.canisterPos = pos;
        this.slot = slot;
        this.initialLabel = currentLabel;
        this.hasLink = hasLink;
    }

    @Override
    protected void init() {
        int centerX = width / HALF;
        int panelHeight = hasLink ? BASE_PANEL_HEIGHT + LINK_ROW_HEIGHT : BASE_PANEL_HEIGHT;
        int centerY = height / HALF;
        int panelLeft = centerX - PANEL_WIDTH / HALF;
        int panelTop = centerY - panelHeight / HALF;

        labelInput = new EditBox(font, panelLeft + INPUT_INSET, panelTop + INPUT_TOP_OFFSET,
            PANEL_WIDTH - INPUT_INSET * HALF, INPUT_HEIGHT, Component.empty());
        labelInput.setMaxLength(ChoralTunerItem.MAX_LABEL_LENGTH);
        labelInput.setValue(initialLabel);
        addRenderableWidget(labelInput);

        int buttonY = panelTop + BUTTON_ROW_OFFSET;
        addRenderableWidget(new TintedButton(
            centerX - BUTTON_WIDTH - BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal(CONFIRM_LABEL).withStyle(Style.EMPTY.withColor(CONFIRM_LABEL_COLOR)),
            this::onConfirm, CONFIRM_BG, CONFIRM_HOVER));
        addRenderableWidget(new TintedButton(
            centerX + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal(CANCEL_LABEL).withStyle(Style.EMPTY.withColor(CANCEL_LABEL_COLOR)),
            this::onCancel, SEVER_BG, SEVER_HOVER));

        if (hasLink) {
            int linkButtonY = buttonY + LINK_ROW_HEIGHT;
            int linkButtonWidth = BUTTON_WIDTH * HALF + INPUT_INSET;
            severButton = new TintedButton(
                centerX - linkButtonWidth / HALF, linkButtonY,
                linkButtonWidth, BUTTON_HEIGHT,
                Component.literal(SEVER_LINK_TEXT),
                this::onSeverLink, SEVER_BG, SEVER_HOVER);
            addRenderableWidget(severButton);
        }

        setFocused(labelInput);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = width / HALF;
        int panelHeight = hasLink ? BASE_PANEL_HEIGHT + LINK_ROW_HEIGHT : BASE_PANEL_HEIGHT;
        int centerY = height / HALF;
        int panelLeft = centerX - PANEL_WIDTH / HALF;
        int panelTop = centerY - panelHeight / HALF;
        guiGraphics.fill(panelLeft, panelTop,
            panelLeft + PANEL_WIDTH, panelTop + panelHeight,
            PANEL_BG_COLOR);
        guiGraphics.centeredText(font, title, centerX, panelTop + TITLE_TOP_OFFSET, COLOR_WHITE);
    }

    /**
     * Sends the rename packet and closes the screen.
     *
     * @param button the button that was pressed
     */
    private void onConfirm(Button button) {
        String label = labelInput.getValue().trim();
        sendToServer(new CanisterRenamePayload(canisterPos, slot, label));
        onClose();
    }

    /**
     * Closes the screen without renaming.
     *
     * @param button the button that was pressed
     */
    private void onCancel(Button button) {
        onClose();
    }

    /**
     * Deadman's switch for severing: first click changes button to "You sure?",
     * second click sends the unlink packet.
     *
     * @param button the button that was pressed
     */
    private void onSeverLink(Button button) {
        if (!severConfirmPending) {
            severConfirmPending = true;
            severButton.setMessage(Component.literal(SEVER_CONFIRM_TEXT)
                .withStyle(Style.EMPTY.withColor(CANCEL_LABEL_COLOR)));
            severButton.setColors(SEVER_CONFIRM_BG, SEVER_CONFIRM_HOVER);
            return;
        }
        sendToServer(new CanisterUnlinkPayload(canisterPos, slot));
        onClose();
    }

    /** Resets the sever button to its initial state. */
    private void resetSeverButton() {
        if (severConfirmPending && severButton != null) {
            severConfirmPending = false;
            severButton.setMessage(Component.literal(SEVER_LINK_TEXT));
            severButton.setColors(SEVER_BG, SEVER_HOVER);
        }
    }

    /**
     * Sends a custom payload to the server.
     *
     * @param payload the network payload
     */
    private static void sendToServer(CustomPacketPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ENTER) {
            onConfirm(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A button with a flat colored background instead of the vanilla texture. */
    static class TintedButton extends Button {

        private int bgColor;
        private int hoverColor;

        /**
         * Creates a tinted button with normal and hover background colors.
         *
         * @param x          the X position
         * @param y          the Y position
         * @param w          the button width
         * @param h          the button height
         * @param message    the button label
         * @param onPress    the click handler
         * @param bgColor    the normal background color
         * @param hoverColor the hover background color
         */
        TintedButton(int x, int y, int w, int h, Component message,
                OnPress onPress, int bgColor, int hoverColor) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.bgColor = bgColor;
            this.hoverColor = hoverColor;
        }

        /**
         * Updates the background colors.
         *
         * @param bgColor the normal background color
         * @param hoverColor the hover background color
         */
        void setColors(int bgColor, int hoverColor) {
            this.bgColor = bgColor;
            this.hoverColor = hoverColor;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX,
                int mouseY, float partialTick) {
            int bg = isHoveredOrFocused() ? hoverColor : bgColor;
            graphics.fill(getX(), getY(),
                getX() + getWidth(), getY() + getHeight(), bg);
            graphics.centeredText(Minecraft.getInstance().font,
                getMessage(), getX() + getWidth() / HALF,
                getY() + (getHeight() - FONT_LINE_HEIGHT) / HALF, COLOR_WHITE);
        }
    }
}
