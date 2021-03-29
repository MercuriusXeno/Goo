package com.xeno.goo.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.events.TargetingHandler;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class CustomIconButton extends Button {
    private final ResourceLocation icon;
    private final boolean toggled;
    public CustomIconButton(int x, int y, int width, int height, ResourceLocation icon,
                            ITextComponent title, IPressable pressedAction, boolean toggled) {
        super(x, y, width, height, title, pressedAction, CustomIconButton::onTooltip);
        this.icon = icon;
        this.toggled = toggled;
    }

    private static void onTooltip(Button button, MatrixStack matrixStack, int x, int y) {
        TargetingHandler.renderConfigName(matrixStack, button.getMessage(), x, y);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        TargetingHandler.renderGooShortIcon(matrixStack, icon, this.x, this.y, this.width, this.height, toggled);

        if (this.isHovered()) {
            this.renderToolTip(matrixStack, mouseX, mouseY);
        }
    }
}
