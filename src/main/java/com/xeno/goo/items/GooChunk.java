package com.xeno.goo.items;

import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.fluids.GooFluid;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class GooChunk extends CrystallizedGooAbstract {
    public GooChunk(Supplier<GooFluid> f, Supplier<Item> source) {
        super(f, source,10000);
    }
}
