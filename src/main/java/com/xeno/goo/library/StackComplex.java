package com.xeno.goo.library;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class StackComplex
{
    public final CompositeOperator operator;
    public final SimpleStack stack;
    public StackComplex(SimpleStack stack, CompositeOperator operation) {
        this.stack = stack;
        this.operator = operation;
    }

    public StackComplex(ItemStack stack, CompositeOperator operator) {
        this.stack = new SimpleStack(stack);
        this.operator = operator;
    }

    public StackComplex(Item item, CompositeOperator operator) {
        this.stack = new SimpleStack(EntryHelper.getSingleton(item));
        this.operator = operator;
    }

    public StackComplex(Item item, int i, CompositeOperator operator) {
        this.stack = new SimpleStack(new ItemStack(item, i));
        this.operator = operator;
    }

    public StackComplex(ItemStack stack) {
        this.stack = new SimpleStack(stack);
        this.operator = CompositeOperator.ADD;
    }

    public StackComplex(Item item) {
        this.stack = new SimpleStack(EntryHelper.getSingleton(item));
        this.operator = CompositeOperator.ADD;
    }

    public StackComplex(Item item, int i) {
        this.stack = new SimpleStack(new ItemStack(item, i));
        this.operator = CompositeOperator.ADD;
    }
}
