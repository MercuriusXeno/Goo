package com.xeno.goo.client.models;

import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemOverrideList;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

public class CrucibleOverridesList extends ItemOverrideList
{
    public CrucibleOverridesList() {

    }

    @Nullable
    @Override
    public IBakedModel func_239290_a_(IBakedModel p_239290_1_, ItemStack p_239290_2_, @Nullable ClientWorld p_239290_3_, @Nullable LivingEntity p_239290_4_)
    {
        return super.func_239290_a_(p_239290_1_, p_239290_2_, p_239290_3_, p_239290_4_);
    }
}
