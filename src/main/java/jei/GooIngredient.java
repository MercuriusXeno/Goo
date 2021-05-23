package jei;

import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TranslationTextComponent;

public class GooIngredient {
	public static final String ID = "goo:goo_ingredient";
	public static final IIngredientType<GooIngredient> GOO = () -> GooIngredient.class;
	private final String fluidKey;
	private final int amount;
	private final ResourceLocation icon;
	public GooIngredient(ResourceLocation fluidKey) {
		this(0, fluidKey);
	}

	public GooIngredient(int i, ResourceLocation fluidKey) {
		this.amount = i;
		this.fluidKey = fluidKey.getPath();
		this.icon = ((GooFluid)Registry.getFluid(fluidKey.toString())).icon();
	}

	public int amount() {
		return amount;
	}

	public String fluidKey() {
		return fluidKey;
	}

	public TranslationTextComponent asTranslatable() {
		return new TranslationTextComponent("goo.amount_of_goo", this.amount);
	}

	public String asString() {
		return asTranslatable().getString();
	}

	public ResourceLocation gooIcon() {
		return icon;
	}
}
