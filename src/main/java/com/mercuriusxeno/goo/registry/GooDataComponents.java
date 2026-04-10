package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.gasket.GasketPairing;
import com.mercuriusxeno.goo.item.gasket.TunerState;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;

public class GooDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Goo.MODID);

    /** Unified multi-type goo volume storage for all containers. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GooContents>> GOO_CONTENTS =
        DATA_COMPONENTS.register("goo_contents",
            () -> DataComponentType.<GooContents>builder()
                .persistent(GooContents.CODEC)
                .networkSynchronized(GooContents.STREAM_CODEC)
                .build());

    /** Canister-specific metadata: matrices, gasket IDs, label. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CanisterMetadata>> CANISTER_METADATA =
        DATA_COMPONENTS.register("canister_metadata",
            () -> DataComponentType.<CanisterMetadata>builder()
                .persistent(CanisterMetadata.CODEC)
                .networkSynchronized(CanisterMetadata.STREAM_CODEC)
                .build());

    /** Gasket pairing data stored on choral gasket items during linking. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GasketPairing>> GASKET_PAIRING =
        DATA_COMPONENTS.register("gasket_pairing",
            () -> DataComponentType.<GasketPairing>builder()
                .persistent(GasketPairing.CODEC)
                .networkSynchronized(GasketPairing.STREAM_CODEC)
                .build());

    /** Remaining fuel ticks on a depleted blaze rod. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FUEL_REMAINING =
        DATA_COMPONENTS.register("fuel_remaining",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .build());

    /** Volume of goo in a blob item, measured in microblobs (mB). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> BLOB_VOLUME =
        DATA_COMPONENTS.register("blob_volume",
            () -> DataComponentType.<Long>builder()
                .persistent(Codec.LONG)
                .networkSynchronized(ByteBufCodecs.VAR_LONG)
                .build());

    /** Canisters stored inside a hub block item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> HUB_CANISTERS =
        DATA_COMPONENTS.register("hub_canisters",
            () -> DataComponentType.<List<ItemStack>>builder()
                .persistent(ItemStack.CODEC.listOf())
                .networkSynchronized(ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()))
                .build());

    /** Selected goo type ID persisted on glove items. Empty string means none. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_GOO_TYPE =
        DATA_COMPONENTS.register("selected_goo_type",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    /** Choral tuner state: owner UUID and in-progress gasket selection. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TunerState>> TUNER_STATE =
        DATA_COMPONENTS.register("tuner_state",
            () -> DataComponentType.<TunerState>builder()
                .persistent(TunerState.CODEC)
                .networkSynchronized(TunerState.STREAM_CODEC)
                .build());
}
