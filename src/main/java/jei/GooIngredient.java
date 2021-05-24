package jei;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.Objects;

public class GooIngredient {
	private static final String ID_PREFIX = "goo:ingredient_";
	public final String id;
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
		this.icon = ((GooFluid)Registry.getFluid(fluidKey.toString())).shortIcon();
		this.id = ID_PREFIX + fluidKey.getPath();
	}

	public int amount() {
		return amount;
	}

	public String fluidKey() {
		return fluidKey;
	}

	public TranslationTextComponent asTranslatable() {
		if (this.amount == 0) {
			return new TranslationTextComponent("fluid.goo." + this.fluidKey);
		}
		return new TranslationTextComponent("goo.amount_of_" + this.fluidKey, this.amount);
	}

	public String asString() {
		return asTranslatable().getString();
	}

	public ResourceLocation gooIcon() {
		return icon;
	}

	public String justAmountAsString() {
		return new TranslationTextComponent("goo.amount_of_goo", this.amount).getString();
	}

	@Override
	public boolean equals(Object o) {

		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GooIngredient that = (GooIngredient) o;
		return id.equals(that.id) && Objects.equals(fluidKey, that.fluidKey) && Objects.equals(icon, that.icon);
	}

	@Override
	public int hashCode() {

		return Objects.hash(fluidKey, icon, id);
	}
}
