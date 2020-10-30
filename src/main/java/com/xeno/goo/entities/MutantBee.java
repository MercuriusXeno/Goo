package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.ItemsRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;

public class MutantBee extends BeeEntity {
    public MutantBee(EntityType<MutantBee> gooBeeEntityType, World world) {
        super(gooBeeEntityType, world);
    }

    public static AttributeModifierMap.MutableAttribute setCustomAttributes() {
        return BeeEntity.func_234182_eX_(); // srg fugly name for the bee entity attributes
    }

    @Override
    public void tick() {
        if (!this.isInLove() && !this.world.isRemote) {
            this.setInLove(600);
        }
        super.tick();
        if (!this.isInLove() && !this.world.isRemote) {
            // TODO spawn bee instead of me
            BeeEntity swapBee = new BeeEntity(EntityType.BEE, this.world);
            CompoundNBT serializeThis = this.serializeNBT();
            this.writeAdditional(serializeThis);
            serializeThis.putString("id", "minecraft:bee"); // overwrite the id
            serializeThis.putUniqueId("UUID", swapBee.getUniqueID());
            swapBee.read(serializeThis);
            swapBee.readAdditional(serializeThis);
            this.world.addEntity(swapBee);
            swapBee.setPositionAndRotation(this.getPosX(), this.getPosY(), this.getPosZ(), this.rotationYaw, this.rotationPitch);
            this.remove();
        }
    }

    // 32 blocks.
    private double A_REASONABLE_RENDER_DISTANCE_SQUARED = 1024;
    @Override
    public boolean isInRangeToRenderDist(double distance)
    {
        return distance < A_REASONABLE_RENDER_DISTANCE_SQUARED;
    }
}
