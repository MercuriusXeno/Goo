package com.mercuriusxeno.goo.ability;

import java.util.HashMap;
import java.util.Map;

/**
 * Named registry of {@link LayerAudio} singletons. JSON ability
 * params carry a string name (e.g. {@code "stone_break"}); the
 * pipeline resolves it once at fuse expiry into the singleton audio
 * profile and then fans out per-step calls without further lookups.
 */
public final class LayerAudioType {

    /** Stone-break sound with low pitch curve. */
    public static final String STONE_BREAK = "stone_break";
    /** Generic-explode sound with bright pitch curve. */
    public static final String GENERIC_EXPLODE = "generic_explode";
    /** No-op for pipelines that emit no per-layer sound. */
    public static final String NONE = "none";

    private static final String UNKNOWN_PREFIX = "Unknown LayerAudio: ";

    private static final Map<String, LayerAudio> AUDIO = new HashMap<>();

    static {
        AUDIO.put(STONE_BREAK, StoneBreakAudio.INSTANCE);
        AUDIO.put(GENERIC_EXPLODE, GenericExplodeAudio.INSTANCE);
        AUDIO.put(NONE, NoneLayerAudio.INSTANCE);
    }

    private LayerAudioType() {
    }

    /**
     * Looks up audio by registered name.
     *
     * @param name the registered audio name
     * @return the singleton audio profile
     * @throws IllegalArgumentException if no audio is registered under {@code name}
     */
    public static LayerAudio byName(String name) {
        LayerAudio audio = AUDIO.get(name);
        if (audio == null) {
            throw new IllegalArgumentException(UNKNOWN_PREFIX + name);
        }
        return audio;
    }

    /**
     * Registers audio under a name. Intended for downstream extension.
     *
     * @param name  the registered name (must be unique)
     * @param audio the audio profile to register
     */
    public static void register(String name, LayerAudio audio) {
        AUDIO.put(name, audio);
    }
}
