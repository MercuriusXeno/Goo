package com.xeno.goo.evaluations;

import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EntryGroup
{
    public List<Item> items;

    public EntryGroup(Item... items) {
        this.items = new ArrayList<>(Arrays.asList(items));
    }
}
