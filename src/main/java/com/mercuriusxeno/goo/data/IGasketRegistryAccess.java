package com.mercuriusxeno.goo.data;

/**
 * Provides access to the {@link GasketRegistry} without requiring a
 * ServerLevel at the call site. The real implementation captures the
 * server reference at init time; tests supply a plain registry.
 */
@FunctionalInterface
public interface IGasketRegistryAccess {

    /**
     * Returns the gasket registry for the current server.
     *
     * @return the server-wide gasket registry
     */
    GasketRegistry get();
}
