package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class Obsidian
{
    private static final int diamondHarvestLevel = 3;
    private static final int bedrockHardness = -1;
    private static final ItemStack mockPick = new ItemStack(Items.DIAMOND_PICKAXE, 1);
    static {
        mockPick.addEnchantment(Enchantments.SILK_TOUCH, 1);
    }
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.OBSIDIAN_GOO.get(), "obsidian_breaker", Obsidian::breaker);
    }

    private static boolean breaker(SplatContext context)
    {
        BlockPos blockPos = context.blockPos();
        BlockState state = context.world().getBlockState(blockPos);
        Vector3d dropPos = Vector3d.copy(blockPos).add(0.5d, 0.5d, 0.5d);

        if (state.getHarvestLevel() <= diamondHarvestLevel && state.getBlockHardness(context.world(), blockPos) != bedrockHardness) {
            if ((context.world() instanceof ServerWorld)) {
                SoundType breakAudio = state.getBlock().getSoundType(state, context.world(), blockPos, null);
                AudioHelper.headlessAudioEvent(context.world(), blockPos, breakAudio.getBreakSound(), SoundCategory.BLOCKS,
                    breakAudio.volume, () -> breakAudio.pitch);
                ((ServerWorld)context.world()).spawnParticle(new BlockParticleData(ParticleTypes.BLOCK, state), dropPos.x, dropPos.y, dropPos.z, 12, 0d, 0d, 0d, 0.15d);
                LootContext.Builder lootBuilder = new LootContext.Builder((ServerWorld) context.world());
                List<ItemStack> drops = state.getDrops(lootBuilder
                        .withParameter(LootParameters.field_237457_g_, context.blockCenterVec())
                        .withParameter(LootParameters.TOOL, mockPick)
                );
                drops.forEach((d) -> context.world().addEntity(
                        new ItemEntity(context.world(), dropPos.getX(), dropPos.getY(), dropPos.getZ(), d)
                ));
                context.world().removeBlock(blockPos, false);

                // now bounce back a bit of goo, but less than what was spent
                // int costToResolve = GooMod.config.costOfInteraction(context.fluid(), context.interactionKey());
                int amountReturned = GooMod.config.returnOfInteraction(context.fluid(), context.interactionKey());

                GooBlob returnBlob = new GooBlob(Registry.GOO_BLOB.get(), context.world(), context.splat().owner(),
                        new FluidStack(context.fluid(), amountReturned), dropPos);
                Vector3d motionVec = Vector3d.copy(context.splat().sideWeLiveOn().getDirectionVec())
                        .scale(0.5d); // unit vector is a little too forceful, dial it back a lot
                returnBlob.setMotion(motionVec);
                context.world().addEntity(returnBlob);
            }


            return true;
        }
        return false;
    }
}
