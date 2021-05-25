package jei;

import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
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

import java.util.*;
import java.util.stream.Collectors;

public class GooRecipeManager implements IRecipeManagerPlugin {
	public static GooRecipeManager instance = new GooRecipeManager();
	private static Map<RegistryKey<World>, List<SolidifierRecipe>> worldSolidifierRecipeCache = new HashMap<>();
	private static Map<RegistryKey<World>, List<GooifierRecipe>> worldGooifierRecipeCache = new HashMap<>();

	@Override
	public <V> List<ResourceLocation> getRecipeCategoryUids(IFocus<V> focus) {
		List<ResourceLocation> validUids = new ArrayList<>();
		if (getRecipeMatches(GooifierRecipeCategory.UID, focus).size() > 0) {
			validUids.add(GooifierRecipeCategory.UID);
		}
		if (getRecipeMatches(SolidifierRecipeCategory.UID, focus).size() > 0) {
			validUids.add(SolidifierRecipeCategory.UID);
		}
		return validUids;
	}

	@Override
	public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
		if (recipeCategory.getUid().equals(GooifierRecipeCategory.UID)) {
			return getRecipeMatches(GooifierRecipeCategory.UID, focus);
		}

		if (recipeCategory.getUid().equals(SolidifierRecipeCategory.UID)) {
			return getRecipeMatches(SolidifierRecipeCategory.UID, focus);
		}

		return Collections.emptyList();
	}

	private <T, V> List<T> getRecipeMatches(ResourceLocation uid, IFocus<V> focus) {
		if (uid.equals(GooifierRecipeCategory.UID)) {
			List<T> gooifierMatches = new ArrayList<>();
			if (focus.getMode().equals(Mode.INPUT)) {
				if (focus.getValue() instanceof ItemStack) {
					ItemStack stack = ((ItemStack) focus.getValue());
					gooifierMatches.addAll((List<T>) worldGooifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.input().isItemEqual(stack))
							.collect(Collectors.toList()));
				}
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				if (focus.getValue() instanceof GooIngredient) {
					GooIngredient stack = ((GooIngredient) focus.getValue());
					gooifierMatches.addAll((List<T>) worldGooifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.outputs().stream().anyMatch(v -> v.fluidKey().equals(stack.fluidKey())))
							.collect(Collectors.toList()));
				}
			}
			gooifierMatches.sort((a, b) -> GooifierRecipe.sortByOutputFocus(a, b, focus));
			return gooifierMatches;
		}
		if (uid.equals(SolidifierRecipeCategory.UID)) {
			List<T> solidifierMatches = new ArrayList<>();
			if (focus.getMode().equals(Mode.INPUT)) {
				if (focus.getValue() instanceof GooIngredient) {
					GooIngredient stack = ((GooIngredient) focus.getValue());
					solidifierMatches.addAll((List<T>) worldSolidifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.inputs().stream().anyMatch(v -> v.fluidKey().equals(stack.fluidKey())))
							.collect(Collectors.toList()));
				}
				if (focus.getValue() instanceof Item) {
					Item stack = ((Item) focus.getValue());
					solidifierMatches.addAll((List<T>) worldGooifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.input().getItem().equals(stack))
							.collect(Collectors.toList()));
				}
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				if (focus.getValue() instanceof ItemStack) {
					ItemStack stack = ((ItemStack) focus.getValue());
					solidifierMatches.addAll((List<T>) worldSolidifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.output().isItemEqual(stack))
							.collect(Collectors.toList()));
				}
				if (focus.getValue() instanceof Item) {
					Item stack = ((Item) focus.getValue());
					solidifierMatches.addAll((List<T>) worldSolidifierRecipeCache.get(Minecraft.getInstance().world.getDimensionKey())
							.stream().filter(k -> k.output().getItem().equals(stack))
							.collect(Collectors.toList()));
				}
			}
			solidifierMatches.sort((a, b) -> SolidifierRecipe.sortByInputFocus(a, b, focus));
			return solidifierMatches;
		}
		return Collections.emptyList();
	}

	@Override
	public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
		if (recipeCategory.getUid().equals(SolidifierRecipeCategory.UID)) {
			return (List<T>) worldSolidifierRecipeCache.getOrDefault(Minecraft.getInstance().world.getDimensionKey(), Collections.emptyList());
		}
		if (recipeCategory.getUid().equals(GooifierRecipeCategory.UID)) {
			return (List<T>) worldGooifierRecipeCache.getOrDefault(Minecraft.getInstance().world.getDimensionKey(), Collections.emptyList());
		}
		return new ArrayList<>();
	}

	public void seedRecipes(RegistryKey<World> worldKey) {
		Map<ICompoundContainer<?>, Set<CompoundInstance>> cache = Equivalencies.cache(worldKey).getAllDataOf(Registry.GOO_GROUP.get());
		Map<Item, GooConversionWrapper> convertedCache = new HashMap<>();
		cache.forEach((k, v) -> {
			if (k.getContents() instanceof ItemStack) {
				GooEntry g = new GooEntry(worldKey, ((ItemStack)k.getContents()).getItem(), v);
				convertedCache.put(((ItemStack)k.getContents()).getItem(), new GooConversionWrapper((ItemStack)k.getContents(), g));
			}
			if (k.getContents() instanceof Item) {
				GooEntry g = new GooEntry(worldKey, ((Item)k.getContents()), v);
				convertedCache.put((Item)k.getContents(), new GooConversionWrapper((Item)k.getContents(), g));
			}
		});
		worldGooifierRecipeCache.put(worldKey, new ArrayList<>());
		worldSolidifierRecipeCache.put(worldKey, new ArrayList<>());
		convertedCache.forEach((k, v) -> {
			if (v.isSolidifiable()) {
				worldSolidifierRecipeCache.get(worldKey).add(v.toSolidifierRecipe());
			}
			if (v.isGooifiable()) {
				worldGooifierRecipeCache.get(worldKey).add(v.toGooifierRecipe());
			}
		});
		GooJeiPlugin.cachedJeiRunTime.getRecipeManager().unhideRecipeCategory(GooifierRecipeCategory.UID);
		GooJeiPlugin.cachedJeiRunTime.getRecipeManager().unhideRecipeCategory(SolidifierRecipeCategory.UID);
	}
}
