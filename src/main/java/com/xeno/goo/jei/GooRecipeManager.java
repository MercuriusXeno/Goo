package com.xeno.goo.jei;

import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooConversionWrapper;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.library.DegraderRecipe;
import com.xeno.goo.library.DegraderRecipes;
import com.xeno.goo.library.MixerRecipe;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocus.Mode;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class GooRecipeManager implements IRecipeManagerPlugin {
	public static GooRecipeManager instance = new GooRecipeManager();

	@Override
	public <V> List<ResourceLocation> getRecipeCategoryUids(IFocus<V> focus) {
		List<ResourceLocation> validUids = new ArrayList<>();
		validUids.add(GooifierRecipeCategory.UID);
		validUids.add(SolidifierRecipeCategory.UID);
		validUids.add(DegraderRecipeCategory.UID);
		validUids.add(MixerRecipeCategory.UID);
		validUids.add(SoulFireRecipeCategory.UID);
		return validUids;
	}

	@Override
	public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
		if (recipeCategory.getUid().equals(GooifierRecipeCategory.UID)) {
			return gooifierRecipes(focus);
		}

		if (recipeCategory.getUid().equals(SolidifierRecipeCategory.UID)) {
			return solidifierRecipes(focus);
		}

		if (recipeCategory.getUid().equals(DegraderRecipeCategory.UID)) {
			return degraderRecipes(focus);
		}

		if (recipeCategory.getUid().equals(MixerRecipeCategory.UID)) {
			return mixerRecipes(focus);
		}

		if (recipeCategory.getUid().equals(SoulFireRecipeCategory.UID)) {
			return soulFireRecipes(focus);
		}

		return Collections.emptyList();
	}

	private <T, V> List<T> mixerRecipes(IFocus<V> focus) {
		List<T> mixerMatches = new ArrayList<>();
		if (focus.getValue() instanceof GooIngredient) {
			if (focus.getMode().equals(Mode.INPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				mixerMatches.addAll((List<T>) getRecipes(category(MixerRecipeCategory.UID))
						.stream().filter(r -> Arrays.stream(((JeiMixerRecipe) r).inputs()).parallel().anyMatch(m -> m.fluidKey().equals(stack.fluidKey())))
						.collect(Collectors.toList())
				);
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				mixerMatches.addAll((List<T>) getRecipes(category(MixerRecipeCategory.UID))
						.stream().filter(r -> ((JeiMixerRecipe) r).output().fluidKey().equals(stack.fluidKey()))
						.collect(Collectors.toList())
				);
			}
		}
		return mixerMatches;
	}

	List<SoulFireRecipe> soulFireRecipes = Arrays.asList(
		new SoulFireRecipe(new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(ItemsRegistry.STYGIAN_WEEPINGS.get()))
	);

	public List<SoulFireRecipe> getJeiSoulFireRecipes() {
		return soulFireRecipes;
	}

	private <T, V> List<T> soulFireRecipes(IFocus<V> focus) {
		List<T> soulFireMatches = new ArrayList<>();
		if (focus.getMode().equals(Mode.INPUT)) {
			if (focus.getValue() instanceof ItemStack) {
				ItemStack stack = ((ItemStack) focus.getValue());
				soulFireMatches.addAll((List<T>) getJeiSoulFireRecipes()
						.stream().filter(k -> k.input().isItemEqual(stack))
						.collect(Collectors.toList()));
			}
			if (focus.getValue() instanceof Item) {
				Item stack = ((Item) focus.getValue());
				soulFireMatches.addAll((List<T>) getJeiSoulFireRecipes()
						.stream().filter(k -> k.input().getItem().equals(stack))
						.collect(Collectors.toList()));
			}
		}
		if (focus.getMode().equals(Mode.OUTPUT)) {
			if (focus.getValue() instanceof ItemStack) {
				ItemStack stack = ((ItemStack) focus.getValue());
				soulFireMatches.addAll((List<T>) getJeiSoulFireRecipes()
						.stream().filter(k -> k.output().isItemEqual(stack))
						.collect(Collectors.toList()));
			}
			if (focus.getValue() instanceof Item) {
				Item stack = ((Item) focus.getValue());
				soulFireMatches.addAll((List<T>) getJeiSoulFireRecipes()
						.stream().filter(k -> k.output().getItem().equals(stack))
						.collect(Collectors.toList()));
			}
		}

		return soulFireMatches;
	}

	private <T, V> List<T> degraderRecipes(IFocus<V> focus) {
		List<T> degraderMatches = new ArrayList<>();
		if (focus.getValue() instanceof GooIngredient) {
			if (focus.getMode().equals(Mode.INPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				degraderMatches.addAll((List<T>) getRecipes(category(DegraderRecipeCategory.UID))
						.stream()
						.filter(r -> ((JeiDegraderRecipe) r).input().fluidKey().equals(stack.fluidKey()))
						.collect(Collectors.toList())
				);
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				degraderMatches.addAll((List<T>) getRecipes(category(DegraderRecipeCategory.UID))
						.stream().filter(r -> ((JeiDegraderRecipe) r).output().fluidKey().equals(stack.fluidKey()))
						.collect(Collectors.toList())
				);
			}
		}
		return degraderMatches;
	}

	private IRecipeCategory category(ResourceLocation uid) {
		return GooJeiPlugin.cachedJeiRunTime.getRecipeManager().getRecipeCategory(uid);
	}

	@NotNull
	private <T, V> List<T> solidifierRecipes(IFocus<V> focus) {

		List<T> solidifierMatches = new ArrayList<>();
		if (focus.getMode().equals(Mode.INPUT)) {
			if (focus.getValue() instanceof GooIngredient) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				solidifierMatches.addAll((List<T>) getJeiSolidifierRecipes()
						.stream().filter(k -> k.inputs().stream().anyMatch(v -> v.fluidKey().equals(stack.fluidKey())))
						.collect(Collectors.toList()));
			}
		} else if (focus.getMode().equals(Mode.OUTPUT)) {
			if (focus.getValue() instanceof ItemStack) {
				ItemStack stack = ((ItemStack) focus.getValue());
				solidifierMatches.addAll((List<T>) getJeiSolidifierRecipes()
						.stream().filter(k -> k.output().isItemEqual(stack))
						.collect(Collectors.toList()));
			}
			if (focus.getValue() instanceof Item) {
				Item stack = ((Item) focus.getValue());
				solidifierMatches.addAll((List<T>) getJeiSolidifierRecipes()
						.stream().filter(k -> k.output().getItem().equals(stack))
						.collect(Collectors.toList()));
			}
		}
		solidifierMatches.sort((a, b) -> SolidifierRecipe.sortByInputFocus(a, b, focus));
		return solidifierMatches;
	}

	@NotNull
	private <T, V> List<T> gooifierRecipes(IFocus<V> focus) {

		List<T> gooifierMatches = new ArrayList<>();
		if (focus.getMode().equals(Mode.INPUT)) {
			if (focus.getValue() instanceof ItemStack) {
				ItemStack stack = ((ItemStack) focus.getValue());
				gooifierMatches.addAll((List<T>) getJeiGooifierRecipes()
						.stream().filter(k -> k.input().isItemEqual(stack))
						.collect(Collectors.toList()));
			}
		} else if (focus.getMode().equals(Mode.OUTPUT)) {
			if (focus.getValue() instanceof GooIngredient) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				gooifierMatches.addAll((List<T>) getJeiGooifierRecipes()
						.stream().filter(k -> k.outputs().stream().anyMatch(v -> v.fluidKey().equals(stack.fluidKey())))
						.collect(Collectors.toList()));
			}
		}
		gooifierMatches.sort((a, b) -> GooifierRecipe.sortByOutputFocus(a, b, focus));
		return gooifierMatches;
	}

	@Override
	public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
		if (recipeCategory.getUid().equals(SolidifierRecipeCategory.UID)) {
			return (List<T>)getJeiSolidifierRecipes();
		}
		if (recipeCategory.getUid().equals(GooifierRecipeCategory.UID)) {
			return (List<T>)getJeiGooifierRecipes();
		}
		if (recipeCategory.getUid().equals(DegraderRecipeCategory.UID)) {
			return convertDegraderRecipesToJeiFormat(DegraderRecipes.recipes());
		}
		if (recipeCategory.getUid().equals(MixerRecipeCategory.UID)) {
			return convertMixerRecipesToJeiFormat(MixerRecipes.recipes());
		}
		if (recipeCategory.getUid().equals(SoulFireRecipeCategory.UID)) {
			return (List<T>)getJeiSoulFireRecipes();
		}
		return new ArrayList<>();
	}

	private List<GooifierRecipe> getJeiGooifierRecipes() {
		return validGooifierRecipes();
	}

	private List<GooifierRecipe> validGooifierRecipes() {
		return aequivaleoCache().values().stream()
				.filter(gooConversionWrapper -> gooConversionWrapper.gooifierRecipe().isPresent())
				.map(gooConversionWrapper -> gooConversionWrapper.gooifierRecipe().get())
				.collect(Collectors.toList());
	}

	private List<SolidifierRecipe> getJeiSolidifierRecipes() {
		return validSolidifierRecipes();
	}

	private List<SolidifierRecipe> validSolidifierRecipes() {
		return aequivaleoCache().values().stream()
				.filter(gooConversionWrapper -> gooConversionWrapper.solidifierRecipe().isPresent())
				.map(gooConversionWrapper -> gooConversionWrapper.solidifierRecipe().get())
				.collect(Collectors.toList());
	}

	private Map<ICompoundContainer<?>, GooConversionWrapper> aequivaleoCache() {
		return Equivalencies.cache(worldKey()).getAllCachedDataOf(Registry.GOO_GROUP.get());
	}

	private <T> List<T> convertMixerRecipesToJeiFormat(List<MixerRecipe> recipes) {
			List<T> result = new ArrayList<>();
			recipes.forEach(r -> result.add((T) convertMixerRecipeToJei(r)));
			return result;
	}

	private Object convertMixerRecipeToJei(MixerRecipe r) {
		return new JeiMixerRecipe(
				new GooIngredient(r.inputs().get(0).getAmount(), r.inputs().get(0).getFluid().getRegistryName()),
				new GooIngredient(r.inputs().get(1).getAmount(), r.inputs().get(1).getFluid().getRegistryName()),
				new GooIngredient(r.output().getAmount(), r.output().getFluid().getRegistryName())
		);
	}

	private <T> List<T> convertDegraderRecipesToJeiFormat(List<DegraderRecipe> recipes) {
		List<T> result = new ArrayList<>();
		recipes.forEach(r -> result.add((T) convertDegraderRecipeToJei(r)));
		return result;
	}

	private JeiDegraderRecipe convertDegraderRecipeToJei(DegraderRecipe r) {
		return new JeiDegraderRecipe(
				new GooIngredient(r.input().getAmount(), r.input().getFluid().getRegistryName()),
				new GooIngredient(r.output().getAmount(), r.output().getFluid().getRegistryName())
		);
	}

	private RegistryKey<World> worldKey() {
		return Minecraft.getInstance().world.getDimensionKey();
	}
}
