package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.CrucibleBlock;
import com.mercuriusxeno.goo.block.PlexerBlock;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.VatBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Block registry for all goo mod blocks, including machine blocks and fluid blocks.
 */
public class GooBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Goo.MODID);

    // --- Fluid blocks (one per goo type) ---
    /** LiquidBlock per goo type, used by the fluid system for in-world placement. */
    public static final Map<GooType, DeferredBlock<LiquidBlock>> FLUID_BLOCKS = new EnumMap<>(GooType.class);

    static {
        for (GooType type : GooType.values()) {
            Supplier<BlockBehaviour.Properties> props = fluidBlockProperties(type);
            FLUID_BLOCKS.put(type, BLOCKS.registerBlock(type.getId() + "_goo",
                    p -> new LiquidBlock(GooFluids.SOURCES.get(type).get(), p),
                    props));
        }
    }

    /** Builds a properties supplier for a goo fluid block with the type's map color. */
    private static Supplier<BlockBehaviour.Properties> fluidBlockProperties(GooType type) {
        return () -> BlockBehaviour.Properties.of()
                .mapColor(mapColorFromGoo(type))
                .liquid()
                .noCollision()
                .strength(-1.0F)
                .noLootTable();
    }

    /** Maps a goo type's RGB color to the nearest vanilla MapColor. */
    private static MapColor mapColorFromGoo(GooType type) {
        return switch (type) {
            case AEON -> MapColor.GOLD;
            case BLAZE -> MapColor.FIRE;
            case CRYSTAL -> MapColor.ICE;
            case ENDER -> MapColor.DIAMOND;
            case FROST -> MapColor.ICE;
            case GLOW -> MapColor.GOLD;
            case HEX -> MapColor.COLOR_BLACK;
            case LEAF -> MapColor.PLANT;
            case METAL -> MapColor.METAL;
            case NETHER -> MapColor.NETHER;
            case PULSE -> MapColor.COLOR_RED;
            case ROCK -> MapColor.STONE;
            case SHROOM -> MapColor.COLOR_PURPLE;
            case TYPHOON -> MapColor.COLOR_LIGHT_GREEN;
            case VITAL -> MapColor.COLOR_RED;
        };
    }

    // --- Machine blocks ---

    private static final Supplier<BlockBehaviour.Properties> CruciblePropertySupplier = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();

    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = BLOCKS.registerBlock("crucible",
            CrucibleBlock::new, CruciblePropertySupplier);

    private static final Supplier<BlockBehaviour.Properties> HubPropertySupplier = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();

    public static final DeferredBlock<HubBlock> HUB = BLOCKS.registerBlock("hub",
            HubBlock::new, HubPropertySupplier);

    private static final Supplier<BlockBehaviour.Properties> PlexerPropertySupplier = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.COPPER);

    public static final DeferredBlock<PlexerBlock> PLEXER = BLOCKS.registerBlock("plexer",
            PlexerBlock::new, PlexerPropertySupplier);

    private static final Supplier<BlockBehaviour.Properties> VatPropertySupplier = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();

    public static final DeferredBlock<VatBlock> VAT = BLOCKS.registerBlock("vat",
            VatBlock::new, VatPropertySupplier);

    private static final Supplier<BlockBehaviour.Properties> TapPropertySupplier = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE).strength(1.5F).sound(SoundType.COPPER)
            .noOcclusion();

    public static final DeferredBlock<TapBlock> TAP = BLOCKS.registerBlock("tap",
            TapBlock::new, TapPropertySupplier);

    private static final Supplier<BlockBehaviour.Properties> CanisterPropertySupplier = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL)
            .noOcclusion();

    public static final DeferredBlock<CanisterBlock> CANISTER = BLOCKS.registerBlock("canister",
            CanisterBlock::new, CanisterPropertySupplier);
}
