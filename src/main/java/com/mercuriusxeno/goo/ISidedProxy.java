package com.mercuriusxeno.goo;

import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Dist-safe proxy for operations that differ between client and server.
 * The client implementation is installed by {@link com.mercuriusxeno.goo.client.GooClientSetup}
 * (Dist.CLIENT only). Common-side code calls {@link #get()} and never
 * references client classes directly.
 */
public interface ISidedProxy {

    /** No-op server proxy. All methods return safe defaults. */
    ISidedProxy SERVER = new ISidedProxy() {};

    /** The active proxy instance. Client on client dist, SERVER on server. */
    ISidedProxy[] INSTANCE = { SERVER };

    /** Returns the active proxy. */
    static ISidedProxy get() {
        return INSTANCE[0];
    }

    /**
     * Returns the current crosshair hit result, or null.
     * Server: always null.
     */
    default @Nullable HitResult getCrosshairHit() {
        return null;
    }
}
