package jei;

import com.google.common.collect.ImmutableList;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class SolidifierRecipeCategory implements IRecipeCategory<SolidifierRecipe> {

	public static final ResourceLocation UID = new ResourceLocation(GooMod.MOD_ID, "solidifier_jei_category");
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable icon;
	private final ItemStack renderStack = new ItemStack(BlocksRegistry.Solidifier.get());

	public SolidifierRecipeCategory(IGuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(150, 60);
		localizedName = I18n.format("block.goo.solidifier");
		icon = guiHelper.createDrawableIngredient(renderStack.copy());
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
		ingredients.setInputs(VanillaTypes.FLUID, recipe.inputs());
		ingredients.setOutput(VanillaTypes.ITEM, recipe.output());
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, SolidifierRecipe recipe, IIngredients ingredients) {
		List<FluidStack> inputs = ingredients.getInputs(VanillaTypes.FLUID).get(0);
		int lastIndex = 0;
		for(int index = 0; index < inputs.size(); index++) {
			recipeLayout.getFluidStacks().init(index, true, 32, 12);
			recipeLayout.getFluidStacks().set(index, ingredients.getInputs(VanillaTypes.FLUID).get(index));
			lastIndex = index;
		}

		recipeLayout.getItemStacks().init(lastIndex + 1, false, 93, 12);
		recipeLayout.getItemStacks().set(lastIndex + 1, ingredients.getOutputs(VanillaTypes.ITEM).get(0));
	}
}
