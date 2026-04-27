package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * NBT and data-component serialization logic for VatBlockEntity.
 */
final class VatSerialization {

    /**
     * NBT key for compression level.
     */
    static final String TAG_COMPRESSION = "Compression";
    /**
     * Legacy NBT key for compression level.
     */
    static final String TAG_MATRICES = "Matrices";
    /**
     * NBT key for goo contents.
     */
    static final String TAG_CONTENTS = "Contents";
    /**
     * NBT key for the player-assigned label.
     */
    static final String TAG_LABEL = "Label";
    /**
     * NBT key for stream goo type ordinal.
     */
    static final String TAG_STREAM_TYPE = "StreamType";
    /**
     * NBT key for stream transfer rate.
     */
    static final String TAG_STREAM_RATE = "StreamRate";
    /**
     * NBT key for stream start tick.
     */
    static final String TAG_STREAM_TICK = "StreamTick";
    /**
     * Sentinel value indicating an invalid NBT ordinal.
     */
    static final int INVALID_ORDINAL = -1;

    private VatSerialization() {
    }

    /**
     * Captures the current stream state from the vat's fluid handler.
     *
     * @param be the vat block entity
     */
    static void snapshotStream(VatBlockEntity be) {
        long tick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        be.vatStreamType = be.fluidHandler.getStreamType(tick);
        be.vatStreamRate = be.fluidHandler.getStreamRate(tick);
        be.vatStreamTick = tick;
    }

    /**
     * Triggers vat stack redistribution if the vat is stacked and not already redistributing.
     *
     * @param be the vat block entity
     */
    static void tryRedistribute(VatBlockEntity be) {
        Level level = be.getLevel();
        if (be.redistributing || !isServerLevel(level)) {
            return;
        }
        if (isStacked(be.getBlockState())) {
            VatStackRedistributor.redistribute(level, be.getBlockPos());
        }
    }

    /**
     * Returns true if the level is a non-null server level.
     *
     * @param level the level to check, or null
     * @return true if the level exists and is server-side
     */
    private static boolean isServerLevel(@Nullable Level level) {
        return level != null && !level.isClientSide();
    }

    /**
     * Returns true if the vat has a neighbor above or below.
     *
     * @param state the vat block state
     * @return true if VAT_ABOVE or VAT_BELOW is set
     */
    private static boolean isStacked(BlockState state) {
        return state.getValue(VatBlock.VAT_ABOVE) || state.getValue(VatBlock.VAT_BELOW);
    }

    /**
     * Writes all vat fields to the value output.
     *
     * @param be     the vat block entity
     * @param output the value output
     */
    static void saveFields(VatBlockEntity be, ValueOutput output) {
        output.putInt(TAG_COMPRESSION, be.compressionLevel);
        saveContentsAndLabel(be, output);
        be.gasketState().save(output);
        saveStreamFields(be, output);
    }

    /**
     * Writes goo contents and label to the value output.
     *
     * @param be     the vat block entity
     * @param output the value output
     */
    private static void saveContentsAndLabel(VatBlockEntity be, ValueOutput output) {
        GooContents contents = be.fluidHandler.toGooContents();
        if (!contents.isEmpty()) {
            output.store(TAG_CONTENTS, GooContents.CODEC, contents);
        }
        if (be.label != null) {
            output.putString(TAG_LABEL, be.label);
        }
    }

    /**
     * Writes stream-state fields to the value output.
     *
     * @param be     the vat block entity
     * @param output the value output
     */
    private static void saveStreamFields(VatBlockEntity be, ValueOutput output) {
        if (be.vatStreamType == null) {
            return;
        }
        output.putInt(TAG_STREAM_TYPE, be.vatStreamType.ordinal());
        output.putInt(TAG_STREAM_RATE, be.vatStreamRate);
        output.putLong(TAG_STREAM_TICK, be.vatStreamTick);
    }

    /**
     * Reads all vat fields from the value input.
     *
     * @param be    the vat block entity
     * @param input the value input
     */
    static void loadFields(VatBlockEntity be, ValueInput input) {
        loadCompression(be, input);
        loadContentsAndLabel(be, input);
        be.gasketState().load(input);
        loadStreamFields(be, input);
    }

    /**
     * Reads and applies compression level from the value input.
     *
     * @param be    the vat block entity
     * @param input the value input
     */
    private static void loadCompression(VatBlockEntity be, ValueInput input) {
        be.compressionLevel = Math.max(0, Math.min(
                input.getIntOr(TAG_COMPRESSION, input.getIntOr(TAG_MATRICES, 0)),
                ContainerCapacity.MAX_COMPRESSION));
        be.syncCapacity();
    }

    /**
     * Reads goo contents and label from the value input.
     *
     * @param be    the vat block entity
     * @param input the value input
     */
    private static void loadContentsAndLabel(VatBlockEntity be, ValueInput input) {
        be.fluidHandler.loadFrom(input.read(TAG_CONTENTS, GooContents.CODEC).orElse(GooContents.EMPTY));
        be.label = input.getString(TAG_LABEL).orElse(null);
    }

    /**
     * Reads stream-state fields from the value input.
     *
     * @param be    the vat block entity
     * @param input the value input
     */
    private static void loadStreamFields(VatBlockEntity be, ValueInput input) {
        int streamOrdinal = input.getIntOr(TAG_STREAM_TYPE, INVALID_ORDINAL);
        be.vatStreamType = resolveStreamType(streamOrdinal);
        be.vatStreamRate = input.getIntOr(TAG_STREAM_RATE, 0);
        be.vatStreamTick = input.getLongOr(TAG_STREAM_TICK, 0);
    }

    /**
     * Resolves stream type from a saved ordinal.
     *
     * @param ordinal the saved ordinal, or INVALID_ORDINAL
     * @return the goo type, or null if invalid
     */
    @Nullable
    static GooType resolveStreamType(int ordinal) {
        GooType[] gooTypes = GooType.values();
        return ordinal >= 0 && ordinal < gooTypes.length ? gooTypes[ordinal] : null;
    }

    /**
     * Writes compression enchantment to the data component builder.
     *
     * @param builder          the component builder
     * @param compressionLevel the compression level (0 = no enchantment)
     * @param level            the current level for registry access, or null
     */
    static void collectCompression(
            DataComponentMap.Builder builder, int compressionLevel, @Nullable Level level) {
        if (compressionLevel > 0 && level != null) {
            level.registryAccess().lookup(Registries.ENCHANTMENT)
                    .flatMap(reg -> reg.get(GooEnchantments.COMPRESSION))
                    .ifPresent(holder -> applyCompression(builder, holder, compressionLevel));
        }
    }

    /**
     * Writes a single compression enchantment entry to the component builder.
     *
     * @param builder the data component builder to write to
     * @param holder  the compression enchantment holder
     * @param level   the compression enchantment level
     */
    private static void applyCompression(DataComponentMap.Builder builder,
                                         Holder<Enchantment> holder, int level) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, level);
        builder.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    /**
     * Writes goo contents to the data component builder.
     *
     * @param builder  the component builder
     * @param contents the goo contents snapshot
     */
    static void collectContents(DataComponentMap.Builder builder, GooContents contents) {
        if (!contents.isEmpty()) {
            builder.set(GooDataComponents.GOO_CONTENTS.get(), contents);
        }
    }

    /**
     * Reads compression level from placed item's enchantments.
     *
     * @param getter the data component getter
     * @return the compression level (0 if none)
     */
    static int applyCompression(DataComponentGetter getter) {
        ItemEnchantments enchants = getter.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            if (entry.getKey().is(GooEnchantments.COMPRESSION)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    /**
     * Reads goo contents from placed item's data components.
     *
     * @param getter the data component getter
     * @return the goo contents, or EMPTY
     */
    static GooContents applyContents(DataComponentGetter getter) {
        return getter.getOrDefault(GooDataComponents.GOO_CONTENTS.get(), GooContents.EMPTY);
    }

}
