package com.xeno.goop.setup;

import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MappingGroup {
    public List<Item> items;

    public MappingGroup(Item... items) {
        this.items = new ArrayList<>(Arrays.asList(items));
    }
}
