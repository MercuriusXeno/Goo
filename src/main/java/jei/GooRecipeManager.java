package jei;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocus.Mode;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;
import java.util.stream.Collectors;

public class GooRecipeManager implements IRecipeManagerPlugin {
	public static GooRecipeManager instance = new GooRecipeManager();
	private static Map<RegistryKey<World>, List<SolidifierRecipe>> worldSolidifierRecipeCache = new HashMap<>();
	private static Map<RegistryKey<World>, List<GooifierRecipe>> worldGooifierRecipeCache = new HashMap<>();

	@Override
	public <V> List<ResourceLocation> getRecipeCategoryUids(IFocus<V> focus) {
		return Arrays.asList(GooifierRecipeCategory.UID, SolidifierRecipeCategory.UID);
	}

	@Override
	public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
		List<T> recipeMatches = new ArrayList<>();
		if (recipeCategory.getUid() == GooifierRecipeCategory.UID) {
			if (focus.getMode().equals(Mode.INPUT)) {
				if (focus.getValue() instanceof ItemStack) {
					ItemStack stack = ((ItemStack)focus.getValue());
					recipeMatches.addAll((List<T>)worldGooifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.input().isItemEqual(stack))
							.collect(Collectors.toList()));
				}
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				if (focus.getValue() instanceof GooIngredient) {
					GooIngredient stack = ((GooIngredient)focus.getValue());
					recipeMatches.addAll((List<T>)worldGooifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.outputs().stream().anyMatch(v -> v.fluidKey() == stack.fluidKey()))
							.collect(Collectors.toList()));
				}
			}
		}
		if (recipeCategory.getUid() == SolidifierRecipeCategory.UID) {
			if (focus.getMode().equals(Mode.INPUT)) {
				if (focus.getValue() instanceof GooIngredient) {
					GooIngredient stack = ((GooIngredient)focus.getValue());
					recipeMatches.addAll((List<T>)worldSolidifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.inputs().stream().anyMatch(v -> v.fluidKey() == stack.fluidKey()))
							.collect(Collectors.toList()));
				}
				if (focus.getValue() instanceof Item) {
					Item stack = ((Item)focus.getValue());
					recipeMatches.addAll((List<T>)worldGooifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.input().getItem().equals(stack))
							.collect(Collectors.toList()));
				}
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				if (focus.getValue() instanceof ItemStack) {
					ItemStack stack = ((ItemStack)focus.getValue());
					recipeMatches.addAll((List<T>)worldSolidifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.output().isItemEqual(stack))
							.collect(Collectors.toList()));
				}
				if (focus.getValue() instanceof Item) {
					Item stack = ((Item)focus.getValue());
					recipeMatches.addAll((List<T>)worldSolidifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.output().getItem().equals(stack))
							.collect(Collectors.toList()));
				}
			}
		}

		return recipeMatches.size() > 0 ? recipeMatches : Collections.emptyList();
	}

	@Override
	public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
		if (recipeCategory.getUid() == SolidifierRecipeCategory.UID) {
			return (List<T>) worldSolidifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey());
		} else if (recipeCategory.getUid() == GooifierRecipeCategory.UID) {
			return (List<T>) worldGooifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey());
		}
		return new ArrayList<>();
	}

	public void seedRecipes(RegistryKey<World> worldKey) {
		Map<ICompoundContainer<?>, Set<CompoundInstance>> cache = Equivalencies.cache(worldKey).getAllDataOf(Registry.GOO_GROUP.get());
		List<GooConversionWrapper> convertedCache = new ArrayList<>();
		cache.forEach((k, v) -> {
			if (k.getContents() instanceof ItemStack) {
				GooEntry g = new GooEntry(worldKey, ((ItemStack)k.getContents()).getItem(), v);
				convertedCache.add(new GooConversionWrapper((ItemStack)k.getContents(), g));
			}
			if (k.getContents() instanceof Item) {
				GooEntry g = new GooEntry(worldKey, ((Item)k.getContents()), v);
				convertedCache.add(new GooConversionWrapper((Item)k.getContents(), g));
			}
		});
		worldGooifierRecipeCache.put(worldKey, new ArrayList<>());
		worldSolidifierRecipeCache.put(worldKey, new ArrayList<>());
		convertedCache.forEach(v -> {
			if (v.isSolidifiable()) {
				worldSolidifierRecipeCache.get(worldKey).add(v.toSolidifierRecipe());
			}
			if (v.isGooifiable()) {
				worldGooifierRecipeCache.get(worldKey).add(v.toGooifierRecipe());
			}
		});

	}
}
