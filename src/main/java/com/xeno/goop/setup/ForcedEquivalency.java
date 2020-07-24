package com.xeno.goop.setup;

import net.minecraft.item.Item;

public class ForcedEquivalency {
    public MappingGroup left;
    public MappingGroup right;

    public ForcedEquivalency (MappingGroup left, MappingGroup right) {
        this.left = left;
        this.right = right;
    }

    public ForcedEquivalency (Item left, Item right) {
        this.left = new MappingGroup(left);
        this.right = new MappingGroup(right);
    }
}
