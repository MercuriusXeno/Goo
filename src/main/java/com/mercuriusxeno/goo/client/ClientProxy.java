package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.ISidedProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Client-side proxy implementation. References {@link Minecraft} directly;
 * only classloaded on the client dist.
 */
public final class ClientProxy implements ISidedProxy {

    @Override
    public @Nullable HitResult getCrosshairHit() {
        return Minecraft.getInstance().hitResult;
    }
}
