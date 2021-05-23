package jei;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.events.TargetingHandler;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;

import java.util.Collections;
import java.util.List;

public class GooIngredientRenderer implements IIngredientRenderer<GooIngredient> {

	@Override
	public void render(MatrixStack matrixStack, int xPosition, int yPosition, GooIngredient ingredient) {
		if (ingredient != null) {
			Minecraft.getInstance().fontRenderer.drawString(matrixStack, ingredient.asString(), xPosition + 1, yPosition + 18, 0xffffff);
			TargetingHandler.renderGooShortIcon(matrixStack, ingredient.gooIcon(), xPosition, yPosition, 16, 16, true);
		}
	}

	@Override
	public List<ITextComponent> getTooltip(GooIngredient ingredient, ITooltipFlag tooltipFlag) {
		return Collections.singletonList(ingredient.asTranslatable());
	}
}
