package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Aggregates data across a connected vat stack for HUD display.
 * Walks up/down from the targeted vat while VAT_ABOVE/VAT_BELOW
 * blockstate properties are true. Pure utility, no side effects.
 */
public final class VatStackAggregator {

    private VatStackAggregator() {
    }

    /**
     * Aggregates vat stack data centered on the targeted position.
     * Contents are summed across all vats in the stack. Matrices and
     * label come from the targeted vat. Cap gasket from the top-most
     * vat, base gasket from the bottom-most.
     *
     * @param level     the level to query
     * @param targetPos the position of the vat the player is targeting
     * @return aggregated stack data, or null if not a valid vat
     */
    public static @Nullable VatStackData aggregate(Level level, BlockPos targetPos) {
        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof VatBlockEntity targetVat)) {
            return null;
        }

        BlockPos topPos = findStackTop(level, targetPos);
        BlockPos bottomPos = findStackBottom(level, targetPos);

        int stackSize = topPos.getY() - bottomPos.getY() + 1;
        GooContents mergedContents = sumContents(level, topPos, bottomPos);
        StackGaskets gaskets = resolveGaskets(level, topPos, bottomPos);

        return buildStackData(targetVat, mergedContents, gaskets, stackSize);
    }

    /**
     * Assembles the final VatStackData from collected components.
     *
     * @param targetVat the targeted vat block entity
     * @param contents  the summed goo contents
     * @param gaskets   the resolved gasket state
     * @param stackSize the number of vats in the stack
     * @return the assembled stack data
     */
    private static VatStackData buildStackData(VatBlockEntity targetVat,
                                               GooContents contents, StackGaskets gaskets, int stackSize) {
        return new VatStackData(
                contents,
                targetVat.getCompressionLevel(),
                gaskets.capGasket(),
                gaskets.baseGasket(),
                targetVat.getLabel(),
                gaskets.capPartner(),
                gaskets.basePartner(),
                stackSize
        );
    }

    /**
     * Walks upward while VAT_ABOVE is true. Returns the top-most vat position.
     *
     * @param level the current level
     * @param start the start block position
     * @return the matching result, or null if not found
     */
    private static BlockPos findStackTop(Level level, BlockPos start) {
        BlockPos pos = start;
        while (level.getBlockState(pos).getValue(VatBlock.VAT_ABOVE)) {
            pos = pos.above();
        }
        return pos;
    }

    /**
     * Walks downward while VAT_BELOW is true. Returns the bottom-most vat position.
     *
     * @param level the current level
     * @param start the start block position
     * @return the matching result, or null if not found
     */
    private static BlockPos findStackBottom(Level level, BlockPos start) {
        BlockPos pos = start;
        while (level.getBlockState(pos).getValue(VatBlock.VAT_BELOW)) {
            pos = pos.below();
        }
        return pos;
    }

    /**
     * Sums GooContents across all vats from top to bottom inclusive.
     *
     * @param level  the current level
     * @param top    whether to render the top cap
     * @param bottom whether to render the bottom cap
     * @return the result
     */
    private static GooContents sumContents(Level level, BlockPos top, BlockPos bottom) {
        GooContents result = GooContents.EMPTY;
        for (int y = top.getY(); y >= bottom.getY(); y--) {
            BlockPos pos = new BlockPos(top.getX(), y, top.getZ());
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VatBlockEntity vat) {
                result = result.mergeWith(vat.getContents());
            }
        }
        return result;
    }

    /**
     * Resolves gasket state from the top-most and bottom-most vats.
     *
     * @param level  the current level
     * @param top    whether to render the top cap
     * @param bottom whether to render the bottom cap
     * @return the resolved result, or null if unresolvable
     */
    private static StackGaskets resolveGaskets(Level level, BlockPos top, BlockPos bottom) {
        boolean capGasket = level.getBlockState(top).getValue(VatBlock.GASKET_CAP);
        boolean baseGasket = level.getBlockState(bottom).getValue(VatBlock.GASKET_BASE);

        GasketPartner capPartner = null;
        GasketPartner basePartner = null;

        BlockEntity topBe = level.getBlockEntity(top);
        if (topBe instanceof VatBlockEntity topVat) {
            capPartner = topVat.getPartner(GasketRole.RECEIVER);
        }
        BlockEntity bottomBe = level.getBlockEntity(bottom);
        if (bottomBe instanceof VatBlockEntity bottomVat) {
            basePartner = bottomVat.getPartner(GasketRole.TRANSMITTER);
        }

        return new StackGaskets(capGasket, baseGasket, capPartner, basePartner);
    }

    /**
     * Gasket state from the top and bottom vats of a stack.
     */
    private record StackGaskets(
            boolean capGasket, boolean baseGasket,
            @Nullable GasketPartner capPartner, @Nullable GasketPartner basePartner) {
    }

    /**
     * Aggregated data for a vat stack. Contents are summed, compression level
     * and label are from the targeted vat, gaskets are from stack endpoints.
     *
     * @param contents    the summed goo contents of the stack
     * @param compression the compression level of the targeted vat
     * @param gasketCap   whether the top vat has a cap gasket
     * @param gasketBase  whether the bottom vat has a base gasket
     * @param label       the label of the targeted vat, or null
     * @param capPartner  the cap gasket's partner, or null
     * @param basePartner the base gasket's partner, or null
     * @param stackSize   the number of vats in the stack
     */
    public record VatStackData(
            GooContents contents, int compression,
            boolean gasketCap, boolean gasketBase,
            @Nullable String label,
            @Nullable GasketPartner capPartner, @Nullable GasketPartner basePartner,
            int stackSize) {

        /**
         * Returns true if either gasket is present.
         *
         * @return true if anyGasket is present
         */
        public boolean hasAnyGasket() {
            return gasketCap || gasketBase;
        }

        /**
         * Returns true if the vat has a non-empty label.
         *
         * @return true if label is present
         */
        public boolean hasLabel() {
            return label != null && !label.isEmpty();
        }
    }
}
