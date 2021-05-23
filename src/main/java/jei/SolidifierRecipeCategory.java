package jei;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

public class SolidifierRecipeCategory implements IRecipeCategory<SolidifierRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "solidifier_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final IDrawable arrow;
	private final ItemStack renderStack = new ItemStack(BlocksRegistry.Solidifier.get());

	public SolidifierRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(150, 60);
		localizedName = I18n.format("block.goo.solidifier");
		icon = guiHelper.createDrawableIngredient(renderStack.copy());
		arrow = guiHelper.createDrawable(new ResourceLocation(GooMod.MOD_ID, "textures/gui/arrow.png"),
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
		ingredients.setInputs(GooIngredient.GOO, recipe.inputs());
		ingredients.setOutput(VanillaTypes.ITEM, recipe.output());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, SolidifierRecipe recipe, IIngredients ingredients) {
		int lastIndex = 0;
		for(int index = 0; index < recipe.inputs().size(); index++) {
			recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(index, true, inputX(index), inputY(index));
			recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(index, recipe.inputs().get(index));
			lastIndex = index;
		}

		recipeLayout.getItemStacks().init(lastIndex + 1, false, 93, 12);
		recipeLayout.getItemStacks().set(lastIndex + 1, recipe.output());
	}

	@Override
	public void draw(SolidifierRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
		arrow.draw(matrixStack, 75, 12);
	}

	private static int inputX(int index) {
		return 12 + (index * 18);
	}

	private static int inputY(int index) {
		return 12 + (index / 5) * 18;
	}

	private static int outputX(int index) {
		return 93;
	}

	private static int outputY(int index) {
		return 12;
	}

	private GooIngredient mouseOverFluidStack(SolidifierRecipe recipe, double mouseX, double mouseY) {
		// figure out if the mouse X is in the range of any fluidstack input, each is 16x16.
		for(int index = 0; index < recipe.inputs().size(); index++) {
			if (mouseX >= inputX(index) && mouseX <= inputX(index) + 16 && mouseY >= inputY(index) && mouseY <= inputY(index) + 16) {
				return recipe.inputs().get(index);
			}
		}

		return null;
	}
}
