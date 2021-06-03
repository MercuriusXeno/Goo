package jei;

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

import java.util.Arrays;

public class MixerRecipeCategory implements IRecipeCategory<JeiMixerRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "mixer_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final IDrawable arrow;
	private final ItemStack renderStack = new ItemStack(BlocksRegistry.Mixer.get());

	public MixerRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(180, 52);
		localizedName = I18n.format("block.goo.mixer");
		icon = guiHelper.createDrawableIngredient(renderStack.copy());
		arrow = guiHelper.createDrawable(new ResourceLocation(GooMod.MOD_ID, "textures/gui/gui_sheet.png"),
				0, 0, 16, 16);
	}

	@Override
	public ResourceLocation getUid() {

		return UID;
	}

	@Override
	public Class<? extends JeiMixerRecipe> getRecipeClass() {

		return JeiMixerRecipe.class;
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
	public void setIngredients(JeiMixerRecipe recipe, IIngredients ingredients) {
		ingredients.setInputs(GooIngredient.GOO, Arrays.asList(recipe.inputs()));
		ingredients.setOutput(GooIngredient.GOO, recipe.output());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, JeiMixerRecipe recipe, IIngredients ingredients) {
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(0, true, inputX(0), inputY(0));
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(0, recipe.inputs()[0]);
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(1, true, inputX(1), inputY(1));
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(1, recipe.inputs()[1]);
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).init(2, false, outputX(0), outputY(0));
		recipeLayout.getIngredientsGroup(GooIngredient.GOO).set(2, recipe.output());
	}

	@Override
	public void draw(JeiMixerRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
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
