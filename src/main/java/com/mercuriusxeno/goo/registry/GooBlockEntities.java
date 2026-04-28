package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class GooBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Goo.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrucibleBlockEntity>> CRUCIBLE =
            BLOCK_ENTITIES.register("crucible",
                    () -> new BlockEntityType<>(CrucibleBlockEntity::new, GooBlocks.CRUCIBLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HubBlockEntity>> HUB =
            BLOCK_ENTITIES.register("hub",
                    () -> new BlockEntityType<>(HubBlockEntity::new, GooBlocks.HUB.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlexerBlockEntity>> PLEXER =
            BLOCK_ENTITIES.register("plexer",
                    () -> new BlockEntityType<>(PlexerBlockEntity::new, GooBlocks.PLEXER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorBlockEntity>> REACTOR =
            BLOCK_ENTITIES.register("reactor",
                    () -> new BlockEntityType<>(ReactorBlockEntity::new, GooBlocks.REACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VatBlockEntity>> VAT =
            BLOCK_ENTITIES.register("vat",
                    () -> new BlockEntityType<>(VatBlockEntity::new, GooBlocks.VAT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TapBlockEntity>> TAP =
            BLOCK_ENTITIES.register("tap",
                    () -> new BlockEntityType<>(TapBlockEntity::new, GooBlocks.TAP.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CanisterBlockEntity>> CANISTER =
            BLOCK_ENTITIES.register("canister",
                    () -> new BlockEntityType<>(CanisterBlockEntity::new, GooBlocks.CANISTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChainMarkerBlockEntity>> CHAIN_MARKER =
            BLOCK_ENTITIES.register("chain_marker",
                    () -> new BlockEntityType<>(ChainMarkerBlockEntity::new, GooBlocks.CHAIN_MARKER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChoralGasketBlockEntity>> CHORAL_GASKET =
            BLOCK_ENTITIES.register("choral_gasket",
                    () -> new BlockEntityType<>(ChoralGasketBlockEntity::new, GooBlocks.CHORAL_GASKET_BLOCK.get()));

}
