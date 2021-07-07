package com.xeno.goo.library;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;

public class MixerRecipe
{
    private List<FluidStack> inputs = new ArrayList<>();
    private FluidStack output;

    public MixerRecipe(FluidStack output, FluidStack...  inputs)
    {
        this.output = output;
        this.inputs.addAll(Arrays.asList(inputs));
    }

    public FluidStack output() {
        return this.output;
    }

    public List<FluidStack> inputs() {
        return this.inputs;
    }

    // mixer recipes are serialized and sent to clients because they're used to dictate
    // animations happening client side. The other recipe systems don't, so that's what this is for.
    public CompoundNBT serializeNbt (CompoundNBT tag) {
        tag.put("output", output().writeToNBT(new CompoundNBT()));
        tag.putInt("input_count", inputs().size());;
        for (int i = 0; i < inputs().size(); i++) {
            tag.put("input_" + i, inputs().get(i).writeToNBT(new CompoundNBT()));
        }
        return tag;
    }

    public static MixerRecipe deserializeNbt(CompoundNBT tag) {
        if (!tag.contains("output")) {
            return null;
        }
        if (!tag.contains("input_count")) {
            return null;
        }
        int count = tag.getInt("input_count");
        FluidStack[] nbtInputs = new FluidStack[count];
        for (int i = 0; i < count; i++) {
            FluidStack f = FluidStack.loadFluidStackFromNBT(tag.getCompound("input_" + i));
            if (f.isEmpty()) {
                return null;
            }
            nbtInputs[i] = f;
        }

        FluidStack output = FluidStack.loadFluidStackFromNBT(tag.getCompound("output"));
        return new MixerRecipe(output, nbtInputs);
    }
}
