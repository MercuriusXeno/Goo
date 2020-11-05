package com.xeno.goo.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.GooMod;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookEntry;

import java.util.function.UnaryOperator;

public class PatchouliConfigs implements ICustomComponent {
    private static final int ICON_WIDTH = 18;
    private static final int ICON_HEIGHT = 18;
    private static final int Y_OFFSET = 18;
    private static final int X_OFFSET = 4;

    @Override
    public void build(int x, int y, int pageNum) {
    }

    @Override
    public void render(MatrixStack matrices, IComponentRenderContext iComponentRenderContext, float ticks, int mouseX, int mouseY) {
        if (iComponentRenderContext instanceof GuiBookEntry) {
            boolean alreadyOn = GooMod.config.gooValuesAlwaysVisible();
            GuiBookEntry castEntry = (GuiBookEntry)iComponentRenderContext;
            ITextComponent buttonTitle = new TranslationTextComponent("patchouli.goo.config.values_visible_without_book");
            Button valuesAlwaysVisibleButton = new CustomIconButton(xOfButton(0), yOfButton(0), ICON_WIDTH, ICON_HEIGHT,
                    Registry.WEIRD_GOO.get().shortIcon(), buttonTitle, PatchouliConfigs::valuesVisibleWithoutBookPressed, alreadyOn);
            castEntry.registerButton(valuesAlwaysVisibleButton, 1, PatchouliConfigs::valuesVisibleWithoutBookPressed);
        }
    }

    private int yOfButton(int i) {
        return Y_OFFSET + ((ICON_HEIGHT + 2) * i);
    }

    private int xOfButton(int i) {
        return X_OFFSET;
    }

    private static void valuesVisibleWithoutBookPressed() {
        boolean alreadyOn = GooMod.config.gooValuesAlwaysVisible();
        GooMod.config.setValuesVisibleWithoutBook(!alreadyOn);
    }

    public static void valuesVisibleWithoutBookPressed(Button button) {
        valuesVisibleWithoutBookPressed();
    }


    @Override
    public boolean mouseClicked(IComponentRenderContext context, double mouseX, double mouseY, int mouseButton) {
        return false;
    }

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> unaryOperator) {
    }
}
