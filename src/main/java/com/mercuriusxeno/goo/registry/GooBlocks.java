package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlock;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.MagickedIceBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlock;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import net.minecraft.world.level.block.Blocks;
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
    /**
     * LiquidBlock per goo type, used by the fluid system for in-world placement.
     */
    public static final Map<GooType, DeferredBlock<LiquidBlock>> FLUID_BLOCKS = new EnumMap<>(GooType.class);

    // --- Fluid blocks (one per goo type) ---
    /**
     * Chain marker: short-lived fuse block for chain world effects.
     */
    public static final DeferredBlock<ChainMarkerBlock> CHAIN_MARKER = BLOCKS.registerBlock(
            "chain_marker", ChainMarkerBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .noCollision()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    // --- Machine blocks ---
    /**
     * Glow crystal: permanent light source left by glow chain detonation.
     */
    public static final DeferredBlock<GlowCrystalBlock> GLOW_CRYSTAL = BLOCKS.registerBlock(
            "glow_crystal", GlowCrystalBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .noCollision()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.GLASS)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .lightLevel(GlowCrystalBlock::lightLevel));
    /**
     * Magicked ice: a non-melting mod variant of vanilla ice, placed
     * permanently by the frost cold snap. Visually, audibly, and
     * mechanically identical to {@code minecraft:ice}. Silk-touch drops
     * vanilla ice; the block is not obtainable in its true form.
     */
    public static final DeferredBlock<MagickedIceBlock> MAGICKED_ICE = BLOCKS.registerBlock(
            "magicked_ice", MagickedIceBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.ICE)
                    .overrideLootTable(Blocks.ICE.getLootTable()));
    /**
     * Indestructible strength value for fluid blocks (matches bedrock).
     */
    private static final float INDESTRUCTIBLE = -1.0F;
    private static final Supplier<BlockBehaviour.Properties> CRUCIBLE_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion()
            .lightLevel(state -> state.getValue(CrucibleBlock.LIT) ? 13 : 0);
    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = BLOCKS.registerBlock("crucible",
            CrucibleBlock::new, CRUCIBLE_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> HUB_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();
    public static final DeferredBlock<HubBlock> HUB = BLOCKS.registerBlock("hub",
            HubBlock::new, HUB_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> PLEXER_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion();
    public static final DeferredBlock<PlexerBlock> PLEXER = BLOCKS.registerBlock("plexer",
            PlexerBlock::new, PLEXER_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> REACTOR_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion();
    /**
     * Reactor: consumes goo from corner canisters, produces output into front hollow.
     */
    public static final DeferredBlock<ReactorBlock> REACTOR = BLOCKS.registerBlock("reactor",
            ReactorBlock::new, REACTOR_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> VAT_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();
    public static final DeferredBlock<VatBlock> VAT = BLOCKS.registerBlock("vat",
            VatBlock::new, VAT_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> TAP_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE).strength(1.5F).sound(SoundType.COPPER)
            .noOcclusion();
    public static final DeferredBlock<TapBlock> TAP = BLOCKS.registerBlock("tap",
            TapBlock::new, TAP_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> CANISTER_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).instabreak().sound(SoundType.METAL)
            .noOcclusion();

    // --- Effect blocks ---
    public static final DeferredBlock<CanisterBlock> CANISTER = BLOCKS.registerBlock("canister",
            CanisterBlock::new, CANISTER_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> CHORAL_GASKET_PROPERTY_SUPPLIER =
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).instabreak().sound(SoundType.AMETHYST)
                    .noOcclusion().noCollision();
    public static final DeferredBlock<ChoralGasketBlock> CHORAL_GASKET_BLOCK = BLOCKS.registerBlock(
            "choral_gasket", ChoralGasketBlock::new, CHORAL_GASKET_PROPERTY_SUPPLIER);
    /**
     * Goo type to vanilla map color mapping.
     */
    private static final Map<GooType, MapColor> GOO_MAP_COLORS = new EnumMap<>(Map.ofEntries(
            Map.entry(GooType.AEON, MapColor.GOLD),
            Map.entry(GooType.BLAZE, MapColor.FIRE),
            Map.entry(GooType.CRYSTAL, MapColor.ICE),
            Map.entry(GooType.ENDER, MapColor.DIAMOND),
            Map.entry(GooType.FROST, MapColor.ICE),
            Map.entry(GooType.GLOW, MapColor.GOLD),
            Map.entry(GooType.HEX, MapColor.COLOR_BLACK),
            Map.entry(GooType.LEAF, MapColor.PLANT),
            Map.entry(GooType.METAL, MapColor.METAL),
            Map.entry(GooType.NETHER, MapColor.NETHER),
            Map.entry(GooType.PULSE, MapColor.COLOR_RED),
            Map.entry(GooType.ROCK, MapColor.STONE),
            Map.entry(GooType.SHROOM, MapColor.COLOR_PURPLE),
            Map.entry(GooType.TYPHOON, MapColor.COLOR_LIGHT_GREEN),
            Map.entry(GooType.VITAL, MapColor.COLOR_RED)));

    static {
        for (GooType type : GooType.values()) {
            Supplier<BlockBehaviour.Properties> props = fluidBlockProperties(type);
            FLUID_BLOCKS.put(type, BLOCKS.registerBlock(type.getId() + "_goo",
                    p -> new LiquidBlock(GooFluids.SOURCES.get(type).get(), p),
                    props));
        }
    }

    /**
     * Builds a properties supplier for a goo fluid block with the type's map color.
     *
     * @param type the goo type
     * @return the block properties supplier
     */
    private static Supplier<BlockBehaviour.Properties> fluidBlockProperties(GooType type) {
        return () -> BlockBehaviour.Properties.of()
                .mapColor(mapColorFromGoo(type))
                .liquid()
                .noCollision()
                .strength(INDESTRUCTIBLE)
                .noLootTable();
    }

    /**
     * Maps a goo type's RGB color to the nearest vanilla MapColor.
     *
     * @param type the goo type
     * @return the corresponding map color
     */
    private static MapColor mapColorFromGoo(GooType type) {
        return GOO_MAP_COLORS.getOrDefault(type, MapColor.STONE);
    }
}
