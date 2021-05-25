package jei;

import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@JeiPlugin
public class GooJeiPlugin implements IModPlugin {
	private static List<GooIngredient> fluids;
	public static IJeiRuntime cachedJeiRunTime;
	private static final ResourceLocation pluginId = new ResourceLocation(GooMod.MOD_ID, "jei_plugin");
	@Override
	public ResourceLocation getPluginUid() {
		return pluginId;
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		cachedJeiRunTime = jeiRuntime;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(
				new SolidifierRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
				new GooifierRecipeCategory(registration.getJeiHelpers().getGuiHelper())
		);
	}

	@Override
	public void registerAdvanced(IAdvancedRegistration registration) {
		registration.addRecipeManagerPlugin(GooRecipeManager.instance);
	}

	@Override
	public void registerIngredients(IModIngredientRegistration registration) {
		fluids = Registry.FluidSuppliers.values().stream().map((e) ->
				new GooIngredient(e.get().getRegistryName())
		).collect(Collectors.toList());

		registration.register(GooIngredient.GOO, fluids, new GooIngredientHelper(), new GooIngredientRenderer());
	}
}
