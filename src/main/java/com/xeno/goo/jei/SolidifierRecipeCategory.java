package com.xeno.goo.jei;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class SolidifierRecipeCategory implements IRecipeCategory<SolidifierRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "solidifier_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final IDrawable arrow;
	private final ItemStack renderStack = new ItemStack(BlocksRegistry.Solidifier.get());

	public SolidifierRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(180, 52);
		localizedName = I18n.format(BlocksRegistry.Solidifier.get().getTranslationKey());
		icon = guiHelper.createDrawableIngredient(renderStack.copy());
		arrow = guiHelper.createDrawable(new ResourceLocation(GooMod.MOD_ID, "textures/gui/gui_sheet.png"),
				0, 0, 16, 16);
	}

	@Override
	public ResourceLocation getUid() {

		return UID;
	}

	@Override
	public Class<? extends SolidifierRecipe> getRecipeClass() {

		return SolidifierRecipe.class;
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
	public void setIngredients(SolidifierRecipe recipe, IIngredients ingredients) {
		if (recipe.inputs().size() == 0 || recipe.output().isEmpty()) {
			GooMod.debug("This is bad");
		}
		ingredients.setInputs(GooIngredient.GOO, recipe.inputs());
		ingredients.setOutput(VanillaTypes.ITEM, recipe.output());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, SolidifierRecipe recipe, IIngredients ingredients) {
		for(int index = 0; index < recipe.inputs().size(); index++) {
			recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(index, true, inputX(index), inputY(index));
			recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(index, recipe.inputs().get(index));
		}

		recipeLayout.getItemStacks().init(recipe.inputs().size() + 1, false, outputX(0), outputY(0));
		recipeLayout.getItemStacks().set(recipe.inputs().size() + 1, recipe.output());
	}

	@Override
	public void draw(SolidifierRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
		arrow.draw(matrixStack, outputX(0) - GooIngredientRenderer.horizontalSpacing, GooIngredientRenderer.comfyPadding + 1);
	}

	private static int inputX(int index) {
		if (index >= GooIngredientRenderer.itemsPerRow) {
			int shiftDifferential = GooIngredientRenderer.horizontalSpacing - GooIngredientRenderer.horizontalSpacing / 2;
			return GooIngredientRenderer.comfyPadding + ((index - GooIngredientRenderer.itemsPerRow) % (GooIngredientRenderer.itemsPerRow + 1)) * GooIngredientRenderer.horizontalSpacing
					+ shiftDifferential;
		}
		return GooIngredientRenderer.comfyPadding + (index % GooIngredientRenderer.itemsPerRow) * GooIngredientRenderer.horizontalSpacing;
	}

	private static int inputY(int index) {
		if (index >= GooIngredientRenderer.itemsPerRow) {
			return GooIngredientRenderer.comfyPadding + ((index - GooIngredientRenderer.itemsPerRow) / (GooIngredientRenderer.itemsPerRow + 1) + 1) * GooIngredientRenderer.verticalSpacing;
		}
		return GooIngredientRenderer.comfyPadding + (index / GooIngredientRenderer.itemsPerRow) * GooIngredientRenderer.verticalSpacing;
	}

	private static int outputX(int index) {
		return GooIngredientRenderer.comfyPadding + GooIngredientRenderer.horizontalSpacing * (GooIngredientRenderer.itemsPerRow + 1);
	}

	private static int outputY(int index) {
		return GooIngredientRenderer.comfyPadding;
	}
}
