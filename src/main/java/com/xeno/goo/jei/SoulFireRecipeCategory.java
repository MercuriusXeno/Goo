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
import net.minecraft.block.Blocks;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;

public class SoulFireRecipeCategory implements IRecipeCategory<SoulFireRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "soul_fire_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final IDrawable arrow;

	public SoulFireRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(180, 52);
		localizedName = I18n.format(Blocks.SOUL_FIRE.getTranslationKey());
		icon = guiHelper.createDrawableIngredient(new ItemStack(Items.SOUL_CAMPFIRE));
		arrow = guiHelper.createDrawable(new ResourceLocation(GooMod.MOD_ID, "textures/gui/gui_sheet.png"),
				0, 0, 16, 16);
	}

	@Override
	public ResourceLocation getUid() {

		return UID;
	}

	@Override
	public Class<? extends SoulFireRecipe> getRecipeClass() {

		return SoulFireRecipe.class;
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
	public void setIngredients(SoulFireRecipe recipe, IIngredients ingredients) {
		ingredients.setInput(VanillaTypes.ITEM, recipe.input());
		ingredients.setOutput(VanillaTypes.ITEM, recipe.output());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, SoulFireRecipe recipe, IIngredients ingredients) {
		recipeLayout.getItemStacks().init(0, true, 0, 0);
		recipeLayout.getItemStacks().set(0, recipe.input());
		recipeLayout.getItemStacks().init(1, false, 36, 0);
		recipeLayout.getItemStacks().set(1, recipe.output());
	}

	@Override
	public void draw(SoulFireRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
		arrow.draw(matrixStack, 18, 1);
	}
}
