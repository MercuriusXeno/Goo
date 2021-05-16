package com.xeno.goo;

import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.setup.*;
import com.xeno.goo.shrink.ShrinkImpl;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.logging.Logger;

@Mod("goo")
public class GooMod
{
    public static final String MOD_ID = "goo";
    public static final Logger logger = Logger.getLogger(MOD_ID);
    public static GooConfig config;
    public static CommonProxy proxy = DistExecutor.safeRunForDist(() -> ClientProxy::new,
            () -> CommonProxy::new);

    public GooMod() {
        Registry.init();

        initializeEventListeners();
    }

    public static void warn(String s) {
        logger.warning(s);
    }

    public static void error(String s) {
        logger.severe(s);
    }

    public static void debug(String s) {
        logger.info(s);
    }

    private void initializeEventListeners()
    {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CommonSetup::init);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CommonSetup::entityAttributeCreation);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CommonSetup::loadComplete);
    }

    public static final ItemGroup ITEM_GROUP = new GooCreativeTab(MOD_ID)
    {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(BlocksRegistry.Solidifier.get());
        }
    };
}
