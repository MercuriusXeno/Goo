package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import java.util.UUID;

/**
 * Custom NeoForge capabilities registered by the goo mod.
 *
 * <p>Gasket capabilities resolve a gasket UUID to the fluid handler at that
 * endpoint. Block targets (canister shelf, hub, vat) use GASKET_BLOCK;
 * entity targets (player inventory) use GASKET_ENTITY.</p>
 */
public final class GooCapabilities {

    /**
     * Block capability for gasket endpoints. Context is the target gasket UUID.
     */
    @SuppressWarnings("unchecked")
    public static final BlockCapability<ResourceHandler<FluidResource>, UUID> GASKET_BLOCK =
            BlockCapability.create(
                    Identifier.fromNamespaceAndPath(Goo.MODID, "gasket_block"),
                    (Class<ResourceHandler<FluidResource>>) (Class<?>) ResourceHandler.class,
                    UUID.class);

    /**
     * Entity capability for gasket endpoints. Context is the target gasket UUID.
     */
    @SuppressWarnings("unchecked")
    public static final EntityCapability<ResourceHandler<FluidResource>, UUID> GASKET_ENTITY =
            EntityCapability.create(
                    Identifier.fromNamespaceAndPath(Goo.MODID, "gasket_entity"),
                    (Class<ResourceHandler<FluidResource>>) (Class<?>) ResourceHandler.class,
                    UUID.class);

    private GooCapabilities() {
    }
}
