package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.fertilize.FertilizeCapability;
import com.xeno.goo.fertilize.FertilizeImpl;
import com.xeno.goo.items.GooChopEffects;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.setup.EntitySpawnConditions;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.shrink.ShrinkImpl;
import com.xeno.goo.shrink.api.ShrinkAPI;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {

    @SubscribeEvent
    public static void onEntityAttacked(AttackEntityEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        if (event.getPlayer().getHeldItemMainhand().getItem().equals(ItemsRegistry.GAUNTLET.get())) {
            if (GooChopEffects.tryDoingChopEffect(event.getPlayer().getHeldItemMainhand(), event.getEntityLiving(), event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityUpdate(EntityEvent event) {
        doCryingObsidianInSoulFireCheck(event);
    }

    private static void doCryingObsidianInSoulFireCheck(EntityEvent event) {
        if (event.getEntity() == null || event.getEntity().world == null) {
            return;
        }
        if (event.getEntity().world.isRemote()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity)) {
            return;
        }
        ItemEntity item = (ItemEntity)event.getEntity();
        if (item.getItem().getItem() != Items.CRYING_OBSIDIAN) {
            return;
        }
        BlockPos pos = item.getPosition();
        if (item.world.getBlockState(pos).getBlock() == Blocks.SOUL_FIRE || item.world.getBlockState(pos).getBlock() == Blocks.SOUL_CAMPFIRE) {
            ItemStack weepings = new ItemStack(ItemsRegistry.STYGIAN_WEEPINGS.get());
            ItemEntity weepingsEntity = new ItemEntity(item.world, item.getPosX(), item.getPosY(), item.getPosZ(), weepings);
            item.world.addEntity(weepingsEntity);
            item.world.addParticle(ParticleTypes.SMOKE, item.getPosX(), item.getPosY(), item.getPosZ(), 0d, 0.1d, 0d);
            AudioHelper.headlessAudioEvent(item.world, pos, SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.NEUTRAL, 1.0f,
                    PitchFormulas.HalfToOne);
            item.remove();
        }
    }

    @SubscribeEvent
    public static void onEntityBreed(BabyEntitySpawnEvent event) {
        if (event.getParentA() instanceof MutantBee && event.getParentB() instanceof MutantBee) {
            event.setChild(new GooBee(Registry.GOO_BEE, event.getParentA().world));
        }
    }

    @SubscribeEvent
    public static void biomeLoad(BiomeLoadingEvent e) {
        EntitySpawnConditions.injectSnailSpawnConditions(e);
    }


    @SubscribeEvent
    public static void attachCaps(AttachCapabilitiesEvent<Entity> evt)
    {
        if(evt.getObject() instanceof LivingEntity)
        {
            evt.addCapability(ShrinkImpl.Provider.NAME, new ShrinkImpl.Provider((LivingEntity) evt.getObject()));
            evt.addCapability(FertilizeImpl.Provider.NAME, new FertilizeImpl.Provider((LivingEntity) evt.getObject()));
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingUpdateEvent event) {

        event.getEntityLiving().getCapability(FertilizeCapability.FERTILIZE_CAPABILITY)
                .ifPresent(iFertilizeProvider -> iFertilizeProvider.setPrevBlockPos(event.getEntityLiving().getPosition()));
    }


    @SubscribeEvent
    public static void playerStartTracking(PlayerEvent.StartTracking event)
    {
        Entity target = event.getTarget();
        PlayerEntity player = event.getPlayer();

        if (player instanceof ServerPlayerEntity && target instanceof LivingEntity)
        {
            LivingEntity livingEntity = (LivingEntity) target;
            livingEntity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider -> iShrinkProvider.sync(livingEntity));
            livingEntity.getCapability(FertilizeCapability.FERTILIZE_CAPABILITY).ifPresent(iFertilizeProvider -> iFertilizeProvider.sync(livingEntity));
        }
    }

    @SubscribeEvent
    public static void joinWorldEvent(EntityJoinWorldEvent event)
    {
        if(!event.getWorld().isRemote && event.getEntity() instanceof LivingEntity)
        {
            LivingEntity livingEntity = (LivingEntity) event.getEntity();
            livingEntity.recalculateSize();
            livingEntity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider -> iShrinkProvider.sync(livingEntity));
            livingEntity.getCapability(FertilizeCapability.FERTILIZE_CAPABILITY).ifPresent(iFertilizeProvider -> iFertilizeProvider.sync(livingEntity));
        }
    }


    @SubscribeEvent
    public static void changeSize(EntityEvent.Size event)
    {
        if(event.getEntity() instanceof LivingEntity)
        {
            LivingEntity livingEntity = (LivingEntity) event.getEntity();
            livingEntity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider ->
            {

                if(iShrinkProvider.isShrunk())
                {
                    event.setNewSize(new EntitySize(iShrinkProvider.widthScale(), iShrinkProvider.heightScale(), true));
                    if(event.getPose() != Pose.STANDING) event.getEntity().setPose(Pose.STANDING);
                    event.setNewEyeHeight(iShrinkProvider.defaultEyeHeight() * iShrinkProvider.scale());
                }
            });
        }
    }
}
