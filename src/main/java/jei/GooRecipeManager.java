package jei;

import com.xeno.goo.aequivaleo.Equivalencies;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GooRecipeManager implements IRecipeManagerPlugin {
	public static GooRecipeManager instance = new GooRecipeManager();

	@Override
	public <V> List<ResourceLocation> getRecipeCategoryUids(IFocus<V> focus) {
		return Arrays.asList(GooifierRecipeCategory.UID, SolidifierRecipeCategory.UID);
	}

	@Override
	public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
		return new ArrayList<T>();
	}

	@Override
	public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
		return new ArrayList<T>();
	}

	public void seedRecipes(World world) {
		Equivalencies.cache(world).

	}
}
