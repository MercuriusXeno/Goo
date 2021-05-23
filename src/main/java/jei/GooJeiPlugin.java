package jei;

import com.xeno.goo.GooMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.util.ResourceLocation;

@JeiPlugin
public class GooJeiPlugin implements IModPlugin {

	private static final ResourceLocation pluginId = new ResourceLocation(GooMod.MOD_ID, "jei_plugin");
	@Override
	public ResourceLocation getPluginUid() {
		return pluginId;
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
}
