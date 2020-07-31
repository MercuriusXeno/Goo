package com.xeno.goop.setup;

import com.xeno.goop.library.GoopMapping;
import com.xeno.goop.library.Helper;
import com.xeno.goop.library.ProgressState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import java.util.Map;
import java.util.TreeMap;

import static com.xeno.goop.library.Compare.itemLexicographicalComparator;
import static com.xeno.goop.library.Compare.stringLexicographicalComparator;

public class ForcedEquivalencies {
    public Map<Item, Item> values = new TreeMap<>(itemLexicographicalComparator);

    public ForcedEquivalencies() {
        this.init();
    }

    public ProgressState pushTo(Map<String, GoopMapping> target) {
        // unlike other direct mappings, equivalencies aren't certain to exist yet
        Map<String, GoopMapping> equivalencyMappings = new TreeMap<>(stringLexicographicalComparator);
        // try fetching equivalencies mappings into a new map
        values.forEach((k, v) -> equivalencyMappings.put(Helper.name(k), target.get(Helper.name(v))));
        return Helper.trackedPush(equivalencyMappings, target);
    }

    private void init () {
        values.put(Items.BLACK_CONCRETE, Items.BLACK_CONCRETE_POWDER);
        values.put(Items.BLUE_CONCRETE, Items.BLUE_CONCRETE_POWDER);
        values.put(Items.BROWN_CONCRETE, Items.BROWN_CONCRETE_POWDER);
        values.put(Items.CYAN_CONCRETE, Items.CYAN_CONCRETE_POWDER);
        values.put(Items.GRAY_CONCRETE, Items.GRAY_CONCRETE_POWDER);
        values.put(Items.GREEN_CONCRETE, Items.GREEN_CONCRETE_POWDER);
        values.put(Items.LIGHT_BLUE_CONCRETE, Items.LIGHT_BLUE_CONCRETE_POWDER);
        values.put(Items.LIGHT_GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE_POWDER);
        values.put(Items.LIME_CONCRETE, Items.LIME_CONCRETE_POWDER);
        values.put(Items.MAGENTA_CONCRETE, Items.MAGENTA_CONCRETE_POWDER);
        values.put(Items.ORANGE_CONCRETE, Items.ORANGE_CONCRETE_POWDER);
        values.put(Items.PINK_CONCRETE, Items.PINK_CONCRETE_POWDER);
        values.put(Items.PURPLE_CONCRETE, Items.PURPLE_CONCRETE_POWDER);
        values.put(Items.RED_CONCRETE, Items.RED_CONCRETE_POWDER);
        values.put(Items.WHITE_CONCRETE, Items.WHITE_CONCRETE_POWDER);
        values.put(Items.YELLOW_CONCRETE, Items.YELLOW_CONCRETE_POWDER);

        values.put(Items.CHARCOAL, Items.COAL);
    }
}
