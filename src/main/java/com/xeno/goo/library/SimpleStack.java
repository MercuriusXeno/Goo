package com.xeno.goo.library;

import net.minecraft.item.ItemStack;

import java.util.Objects;

public class SimpleStack
{
    public final String resource;
    public final int count;
    public SimpleStack(String resource, int count) {
        this.resource = resource;
        this.count = count;
    }

    public SimpleStack(ItemStack stack) {
        this.resource = Objects.requireNonNull(stack.getItem().getRegistryName()).toString();
        this.count = stack.getCount();
    }
}
