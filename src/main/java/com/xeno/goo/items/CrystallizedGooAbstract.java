package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class CrystallizedGooAbstract extends Item {
    private static final ResourceLocation vanillaBeeResourceLocation =
            new ResourceLocation("bee");
    public static final ResourceLocation crystalBeeBreedingItemResourceLocation =
            new ResourceLocation(GooMod.MOD_ID, "chromatic_goo_crystal");
    private static final Supplier<EntityType<?>> vanillaBeeTypeSupplier =
            () -> ForgeRegistries.ENTITIES.getValue(vanillaBeeResourceLocation);
    private static final RegistryObject<CrystallizedGooAbstract> crystalBeeBreedingItemSupplier =
            ItemsRegistry.CrystallizedGoo.get(crystalBeeBreedingItemResourceLocation);
    private final Supplier<GooFluid> gooType;
    private final Supplier<Item> crystalFrom;
    private final int gooValue;
    public CrystallizedGooAbstract(Supplier<GooFluid> gooType, Supplier<Item> crystalFrom, int value) {
        super(new Properties().group(GooMod.ITEM_GROUP));
        this.gooType = gooType;
        this.crystalFrom = crystalFrom;
        this.gooValue = value;
    }

    public Item source() { return crystalFrom.get(); }

    public Fluid gooType() {
        return gooType.get();
    }

    public int amount() {
        return gooValue;
    }

    @Override
    public int getBurnTime(ItemStack itemStack) {
        return this.gooType.equals(Registry.MOLTEN_GOO) ? gooValue * 20 : 0;
    }


    @Override
    public ActionResultType itemInteractionForEntity(ItemStack stack, PlayerEntity playerIn, LivingEntity target, Hand hand) {
        return tryBeeMutation(stack, playerIn, target, hand);
    }

    private ActionResultType tryBeeMutation(ItemStack stack, PlayerEntity playerIn, LivingEntity target, Hand hand) {
        if (isValidItemForMutation(stack) && isValidForMutation(target)) {
            if (!playerIn.world.isRemote) {
                MutantBee swapBee = new MutantBee(Registry.MUTANT_BEE.get(), playerIn.world);
                CompoundNBT serializeBee = target.serializeNBT();
                swapBee.writeAdditional(serializeBee);
                serializeBee.putString("id", "goo:mutant_bee"); // overwrite the
                serializeBee.putUniqueId("UUID", swapBee.getUniqueID());
                swapBee.read(serializeBee);
                swapBee.readAdditional(serializeBee);
                playerIn.world.addEntity(swapBee);
                swapBee.setPositionAndRotation(target.getPosX(), target.getPosY(), target.getPosZ(),
                        target.rotationYaw, target.rotationPitch);
                target.remove();
            }
        }
        return super.itemInteractionForEntity(stack, playerIn, target, hand);
    }

    private boolean isValidItemForMutation(ItemStack stack) {
        return stack.getItem().equals(crystalBeeBreedingItemSupplier.get());
    }

    private boolean isValidForMutation(LivingEntity target) {
        if (!target.getType().equals(vanillaBeeTypeSupplier.get())) {
            return false;
        }

        BeeEntity e = (BeeEntity)target;
        return !e.isInLove() && e.getGrowingAge() >= 0;
    }
}
