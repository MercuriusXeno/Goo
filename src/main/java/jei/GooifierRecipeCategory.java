package jei;

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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GooifierRecipeCategory implements IRecipeCategory<GooifierRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "gooifier_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final IDrawable arrow;
	private final ItemStack renderStack = new ItemStack(BlocksRegistry.Gooifier.get());

	public GooifierRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(150, 60);
		localizedName = I18n.format("block.goo.gooifier");
		icon = guiHelper.createDrawableIngredient(renderStack.copy());
		arrow = guiHelper.createDrawable(new ResourceLocation(GooMod.MOD_ID, "textures/gui/arrow.png"),
				0, 0, 16, 16);
	}

	@Override
	public ResourceLocation getUid() {

		return UID;
	}

	@Override
	public Class<? extends GooifierRecipe> getRecipeClass() {

		return GooifierRecipe.class;
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
	public void setIngredients(GooifierRecipe recipe, IIngredients ingredients) {
		ingredients.setInput(VanillaTypes.ITEM, recipe.input());
		ingredients.setOutputs(GooIngredient.GOO, recipe.outputs());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, GooifierRecipe recipe, IIngredients ingredients) {
		recipeLayout.getItemStacks().init(0, true, 12, 12);
		recipeLayout.getItemStacks().set(0, recipe.input());

		for(int index = 0; index <= recipe.outputs().size(); index++) {
			recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(index + 1, false, outputX(index), outputY(index));
			recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(index + 1, recipe.outputs().get(index));
		}
	}

	@Override
	public void draw(GooifierRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
		arrow.draw(matrixStack, 30, 12);
	}

	private static int inputX(int index) {
		return 12;
	}

	private static int inputY(int index) {
		return 12;
	}

	private static int outputX(int index) {
		return 50 + (index * 18);
	}

	private static int outputY(int index) {
		return 12 + (index / 5) * 18;
	}

	private GooIngredient mouseOverFluidStack(GooifierRecipe recipe, double mouseX, double mouseY) {
		// figure out if the mouse X is in the range of any fluidstack input, each is 16x16.
		for(int index = 0; index < recipe.outputs().size(); index++) {
			if (mouseX >= outputX(index) && mouseX <= outputX(index) + 16 && mouseY >= outputY(index) && mouseY <= outputY(index) + 16) {
				return recipe.outputs().get(index);
			}
		}

		return null;
	}
}
