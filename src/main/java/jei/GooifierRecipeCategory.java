package jei;

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
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class GooifierRecipeCategory implements IRecipeCategory<GooifierRecipe> {

	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "gooifier_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final ItemStack renderStack = new ItemStack(BlocksRegistry.Gooifier.get());

	public GooifierRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(150, 60);
		localizedName = I18n.format("block.goo.gooifier");
		icon = guiHelper.createDrawableIngredient(renderStack.copy());
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
		ingredients.setOutputs(VanillaTypes.FLUID, recipe.outputs());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, GooifierRecipe recipe, IIngredients ingredients) {
		recipeLayout.getItemStacks().init(0, true, 93, 12);
		recipeLayout.getItemStacks().set(0, ingredients.getInputs(VanillaTypes.ITEM).get(0));

		List<FluidStack> outputs = ingredients.getOutputs(VanillaTypes.FLUID).get(0);
		for(int index = 1; index <= outputs.size(); index++) {
			recipeLayout.getFluidStacks().init(index, false, 32, 12);
			recipeLayout.getFluidStacks().set(index, ingredients.getOutputs(VanillaTypes.FLUID).get(index));
		}
	}
}
