package com.xeno.goo.entities;

import com.xeno.goo.setup.Registry;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.Nullable;

public class GooSnail extends AnimalEntity implements IEntityAdditionalSpawnData {
    private int ticksMoving;
    // determines the snail's speed indirectly.
    private static int MOTION_FULL_STEP = 40;
    private static int MOTION_HALF_STEP = MOTION_FULL_STEP / 2;
    public GooSnail(EntityType<? extends AnimalEntity> type, World worldIn) {
        super(type, worldIn);
        ticksMoving = 0;
    }

    @Nullable
    @Override
    public AgeableEntity func_241840_a(ServerWorld p_241840_1_, AgeableEntity p_241840_2_) {
        return Registry.GOO_SNAIL.get().create(world);
    }

    public float getBodyStretch(float partialTick) {
        if (this.isInMotion()) {
            return ticksMoving % MOTION_FULL_STEP / (float)MOTION_HALF_STEP;
        }
        return 0f;
    }

    private boolean isInMotion() {
        return ticksMoving > 0;
    }

    public static AttributeModifierMap.MutableAttribute setCustomAttributes() {
        return MobEntity.func_233666_p_()
                .createMutableAttribute(Attributes.MAX_HEALTH, 10D)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.3F)
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D)
                .createMutableAttribute(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {

    }

    @Override
    public void readSpawnData(PacketBuffer additionalData) {

    }


    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
    }

    protected void registerData() {
        super.registerData();
    }
}
