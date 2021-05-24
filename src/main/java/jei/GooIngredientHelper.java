package jei;

import com.xeno.goo.GooMod;
import mezz.jei.api.ingredients.IIngredientHelper;

public class GooIngredientHelper implements IIngredientHelper<GooIngredient> {

	@Override
	public GooIngredient getMatch(Iterable<GooIngredient> ingredients, GooIngredient ingredientToMatch) {
		for (GooIngredient gooIngredient : ingredients) {
			if (gooIngredient.fluidKey().equals(ingredientToMatch.fluidKey())) {
				return gooIngredient;
			}
		}
		return null;
	}

	@Override
	public String getDisplayName(GooIngredient ingredient) {

		return ingredient.asString();
	}

	@Override
	public String getUniqueId(GooIngredient ingredient) {

		return ingredient.id;
	}

	@Override
	public String getModId(GooIngredient ingredient) {

		return GooMod.MOD_ID;
	}

	@Override
	public String getResourceId(GooIngredient ingredient) {

		return ingredient.id;
	}

	@Override
	public GooIngredient copyIngredient(GooIngredient ingredient) {

		return ingredient;
	}

	@Override
	public String getErrorInfo(GooIngredient ingredient) {
		if (ingredient == null) {
			return "Obviously 'null' is not a valid goo...";
		}
		return ingredient.asString();
	}
}
