package com.xeno.goo.client.models;
import net.minecraft.util.Direction;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.math.vector.Vector3f;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// stolen from slime knights mantle
public class FluidCuboid {
    public static final Map<Direction, FluidFace> DEFAULT_FACES;
    static {
        DEFAULT_FACES = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            DEFAULT_FACES.put(direction, FluidFace.NORMAL);
        }
    }

    /** Fluid start, scaled for block models */
    private final Vector3f from;
    /** Fluid end, scaled for block models */
    private final Vector3f to;
    /** Block faces for the fluid */
    private final Map<Direction, FluidFace> faces;

    /** Cache for scaled from */
    private Vector3f fromScaled;
    /** Cache for scaled to */
    private Vector3f toScaled;

    public FluidCuboid(Vector3f from, Vector3f to, Map<Direction,FluidFace> faces) {
        this.from = from;
        this.to = to;
        this.faces = faces;
    }

    /**
     * Checks if the fluid has the given face
     * @param face  Face to check
     * @return  True if the face is present
     */
    public FluidFace getFace(Direction face) {
        return faces.get(face);
    }

    /**
     * Gets fluid from, scaled for renderer
     * @return Scaled from
     */
    public Vector3f getFromScaled() {
        if (fromScaled == null) {
            fromScaled = from.copy();
            fromScaled.mul(1 / 16f);
        }
        return fromScaled;
    }

    /**
     * Gets fluid to, scaled for renderer
     * @return Scaled from
     */
    public Vector3f getToScaled() {
        if (toScaled == null) {
            toScaled = to.copy();
            toScaled.mul(1 / 16f);
        }
        return toScaled;
    }

    public static class FluidFace {
        public static final FluidFace NORMAL = new FluidFace(false, 0);
        private final boolean isFlowing;
        private final int rotation;

        public FluidFace(boolean isFlowing, int rotation) {
            this.isFlowing = isFlowing;
            this.rotation = rotation;
        }

        public boolean isFlowing() {
            return this.isFlowing;
        }

        public int rotation() {
            return this.rotation;
        }
    }
}