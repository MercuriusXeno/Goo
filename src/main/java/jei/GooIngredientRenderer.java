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
	public static int horizontalSpacing = 24;
	public static int verticalSpacing = 22;
	public static int itemsPerRow = 4;
	public static int comfyPadding = 1;
	public static float fontScale = 0.5f;

	@Override
	public void render(MatrixStack matrixStack, int xPosition, int yPosition, GooIngredient ingredient) {
		if (ingredient != null) {
			TargetingHandler.renderGooShortIcon(matrixStack, ingredient.gooIcon(), xPosition, yPosition, 16, 16, true);
			if (ingredient.amount() > 0) {
				matrixStack.push();
				matrixStack.translate(xPosition, yPosition + 18, 1);
				matrixStack.scale(fontScale, fontScale, fontScale);
				Minecraft.getInstance().fontRenderer.drawStringWithShadow(matrixStack, ingredient.justAmountAsString(), 0, 0, 0xffffff);
				matrixStack.pop();
			}
		}
	}

	@Override
	public List<ITextComponent> getTooltip(GooIngredient ingredient, ITooltipFlag tooltipFlag) {
		return Collections.singletonList(ingredient.asTranslatable());
	}
}
