package com.xeno.goo.jei;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.items.ItemsRegistry;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;

public class CrucibleRecipeCategory implements IRecipeCategory<JeiCrucibleRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "crucible_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final IDrawable arrow;

	public CrucibleRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(180, 52);
		localizedName = I18n.format(BlocksRegistry.Crucible.get().getTranslationKey());
		icon = guiHelper.createDrawableIngredient(new ItemStack(ItemsRegistry.CRUCIBLE.get()));
		arrow = guiHelper.createDrawable(new ResourceLocation(GooMod.MOD_ID, "textures/gui/gui_sheet.png"),
				0, 0, 16, 16);
	}

	@Override
	public ResourceLocation getUid() {

		return UID;
	}

	@Override
	public Class<? extends JeiCrucibleRecipe> getRecipeClass() {

		return JeiCrucibleRecipe.class;
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
	public void setIngredients(JeiCrucibleRecipe recipe, IIngredients ingredients) {
		ingredients.setInputs(VanillaTypes.ITEM, Arrays.asList(recipe.itemInput()));
		ingredients.setInputs(GooIngredient.GOO, Arrays.asList(recipe.gooInput()));
		ingredients.setOutput(VanillaTypes.ITEM, recipe.output());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, JeiCrucibleRecipe recipe, IIngredients ingredients) {
		recipeLayout.getItemStacks().init(0, true, 0, 0);
		recipeLayout.getItemStacks().set(0, recipe.itemInput());
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(1, true, 18, 0);
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(1, recipe.gooInput());
		recipeLayout.getItemStacks().init(2, false, 58, 0);
		recipeLayout.getItemStacks().set(2, recipe.output());
	}

	@Override
	public void draw(JeiCrucibleRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {

		arrow.draw(matrixStack, 38, 0);
	}
}
