package com.xeno.goo.jei;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class DegraderRecipeCategory implements IRecipeCategory<JeiDegraderRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "degrader_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final IDrawable arrow;
	private final ItemStack renderStack = new ItemStack(BlocksRegistry.Degrader.get());

	public DegraderRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(180, 52);
		localizedName = I18n.format("block.goo.degrader");
		icon = guiHelper.createDrawableIngredient(renderStack.copy());
		arrow = guiHelper.createDrawable(new ResourceLocation(GooMod.MOD_ID, "textures/gui/gui_sheet.png"),
				0, 0, 16, 16);
	}

	@Override
	public ResourceLocation getUid() {

		return UID;
	}

	@Override
	public Class<? extends JeiDegraderRecipe> getRecipeClass() {

		return JeiDegraderRecipe.class;
	}

	@Override
	public String getTitle() {

		return localizedName;
	}

	@Override
	public IDrawable getBackground() {

		return background;
	}

	@Override
	public IDrawable getIcon() {

		return icon;
	}

	@Override
	public void setIngredients(JeiDegraderRecipe recipe, IIngredients ingredients) {
		ingredients.setInput(GooIngredient.GOO, recipe.input());
		ingredients.setOutput(GooIngredient.GOO, recipe.output());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, JeiDegraderRecipe recipe, IIngredients ingredients) {
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(0, true, inputX(0), inputY(0));
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(0, recipe.input());
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(1, false, outputX(0), outputY(0));
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(1, recipe.output());
	}

	@Override
	public void draw(JeiDegraderRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
		arrow.draw(matrixStack, outputX(0) - GooIngredientRenderer.horizontalSpacing, GooIngredientRenderer.comfyPadding + 1);
	}

	private static int inputX(int index) {
		return GooIngredientRenderer.comfyPadding;
	}

	private static int inputY(int index) {
		return GooIngredientRenderer.comfyPadding;
	}

	private static int outputX(int index) {
		if (index >= GooIngredientRenderer.itemsPerRow) {
			int shiftDifferential = GooIngredientRenderer.horizontalSpacing - GooIngredientRenderer.horizontalSpacing / 2;
			return GooIngredientRenderer.comfyPadding +
					(((index - GooIngredientRenderer.itemsPerRow) % (GooIngredientRenderer.itemsPerRow + 1)) + 2) * GooIngredientRenderer.horizontalSpacing -
					shiftDifferential;
		}
		return GooIngredientRenderer.comfyPadding + ((index % GooIngredientRenderer.itemsPerRow) + 2) * GooIngredientRenderer.horizontalSpacing;
	}

	private static int outputY(int index) {
		if (index >= GooIngredientRenderer.itemsPerRow) {
			return GooIngredientRenderer.comfyPadding + ((index - GooIngredientRenderer.itemsPerRow) / (GooIngredientRenderer.itemsPerRow + 1) + 1) *  GooIngredientRenderer.verticalSpacing;
		}
		return GooIngredientRenderer.comfyPadding + (index / GooIngredientRenderer.itemsPerRow) *  GooIngredientRenderer.verticalSpacing;
	}
}
