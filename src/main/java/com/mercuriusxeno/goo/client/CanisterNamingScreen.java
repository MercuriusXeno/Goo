package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.item.ChoralTunerItem;
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

    private final BlockPos canisterPos;
    private final int slot;
    private final String initialLabel;
    private final boolean hasLink;
    private EditBox labelInput;
    private TintedButton severButton;
    private boolean severConfirmPending = false;

    /** Creates the naming screen for the machine at the given position and slot. */
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
        int centerX = width / 2;
        int panelHeight = hasLink ? BASE_PANEL_HEIGHT + LINK_ROW_HEIGHT : BASE_PANEL_HEIGHT;
        int centerY = height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - panelHeight / 2;

        labelInput = new EditBox(font, panelLeft + 10, panelTop + 25,
            PANEL_WIDTH - 20, 20, Component.empty());
        labelInput.setMaxLength(ChoralTunerItem.MAX_LABEL_LENGTH);
        labelInput.setValue(initialLabel);
        addRenderableWidget(labelInput);

        int buttonY = panelTop + 55;
        addRenderableWidget(new TintedButton(
            centerX - BUTTON_WIDTH - 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal("\u2713").withStyle(Style.EMPTY.withColor(0x55FF55)),
            this::onConfirm, 0xFF1A3A1A, 0xFF2A5A2A));
        addRenderableWidget(new TintedButton(
            centerX + 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal("\u2717").withStyle(Style.EMPTY.withColor(0xFF5555)),
            this::onCancel, 0xFF3A1A1A, 0xFF5A2A2A));

        if (hasLink) {
            int linkButtonY = buttonY + LINK_ROW_HEIGHT;
            int linkButtonWidth = BUTTON_WIDTH * 2 + 10;
            severButton = new TintedButton(
                centerX - linkButtonWidth / 2, linkButtonY,
                linkButtonWidth, BUTTON_HEIGHT,
                Component.literal("Sever Link"),
                this::onSeverLink, SEVER_BG, SEVER_HOVER);
            addRenderableWidget(severButton);
        }

        setFocused(labelInput);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int panelHeight = hasLink ? BASE_PANEL_HEIGHT + LINK_ROW_HEIGHT : BASE_PANEL_HEIGHT;
        int centerY = height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - panelHeight / 2;
        guiGraphics.fill(panelLeft, panelTop,
            panelLeft + PANEL_WIDTH, panelTop + panelHeight,
            0xCC000000);
        guiGraphics.centeredText(font, title, centerX, panelTop + 8, 0xFFFFFFFF);
    }

    /** Sends the rename packet and closes the screen. */
    private void onConfirm(Button button) {
        String label = labelInput.getValue().trim();
        sendToServer(new CanisterRenamePayload(canisterPos, slot, label));
        onClose();
    }

    /** Closes the screen without renaming. */
    private void onCancel(Button button) {
        onClose();
    }

    /**
     * Deadman's switch for severing: first click changes button to "You sure?",
     * second click sends the unlink packet.
     */
    private void onSeverLink(Button button) {
        if (!severConfirmPending) {
            severConfirmPending = true;
            severButton.setMessage(Component.literal("You sure?")
                .withStyle(Style.EMPTY.withColor(0xFF5555)));
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
            severButton.setMessage(Component.literal("Sever Link"));
            severButton.setColors(SEVER_BG, SEVER_HOVER);
        }
    }

    /** Sends a custom payload to the server. */
    private static void sendToServer(CustomPacketPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257) {
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

        /** Creates a tinted button with normal and hover background colors. */
        TintedButton(int x, int y, int w, int h, Component message,
                OnPress onPress, int bgColor, int hoverColor) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.bgColor = bgColor;
            this.hoverColor = hoverColor;
        }

        /** Updates the background colors. */
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
                getMessage(), getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
        }
    }
}
