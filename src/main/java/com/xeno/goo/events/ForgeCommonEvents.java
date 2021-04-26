package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.items.GooChopEffects;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.GooConfig;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.EntityClassification;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biome.Category;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {

    @SubscribeEvent
    public static void onEntityAttacked(AttackEntityEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        if (event.getPlayer().getHeldItemMainhand().getItem().equals(ItemsRegistry.Gauntlet.get())) {
            if (GooChopEffects.tryDoingChopEffect(event.getPlayer().getHeldItemMainhand(), event.getEntityLiving(), event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityBreed(BabyEntitySpawnEvent event) {
        if (event.getParentA() instanceof MutantBee && event.getParentB() instanceof MutantBee) {
            event.setChild(new GooBee(Registry.GOO_BEE.get(), event.getParentA().world));
        }
    }

    // borrowed from Ars Nouveau
    // normal biomes we're okay with snails spawning in caves in
    private static List<Category> VALID_SNAIL_SPAWN_BIOMES = Arrays.asList(Biome.Category.FOREST, Biome.Category.EXTREME_HILLS, Biome.Category.JUNGLE,
            Biome.Category.PLAINS, Biome.Category.SWAMP, Biome.Category.SAVANNA);

    @SubscribeEvent
    public static void biomeLoad(BiomeLoadingEvent e) {
        if (VALID_SNAIL_SPAWN_BIOMES.contains(e.getCategory())) {
            if (GooMod.config.snailSpawnWeight() > 0) {
                MobSpawnInfo.Spawners spawnInfo = new MobSpawnInfo.Spawners(Registry.GOO_SNAIL.get(), GooMod.config.snailSpawnWeight(), 1, 1);
                e.getSpawns().withSpawner(EntityClassification.getClassificationByName("goo:goo_snail"), spawnInfo);
            }
        }
    }
}
