package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.network.BlobFlightPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side manager for active blob flights. Tracks each flight from
 * throw to arrival, providing interpolated position each frame for rendering
 * and particle spawning.
 */
public final class BlobFlightManager {

    /** Active flights, keyed by a monotonically increasing ID. */
    /** Half-block offset for centering on a face. */
    private static final double FACE_CENTER_OFFSET = 0.5;
    /** Divisor for computing entity vertical center. */
    private static final double ENTITY_CENTER_DIVISOR = 2.0;
    /** Epsilon for near-zero length detection in direction vectors. */
    private static final double DIRECTION_EPSILON = 1e-6;

    private static final Map<Integer, BlobFlight> FLIGHTS = new ConcurrentHashMap<>();
    private static int nextId;
        /** Velocity finite-difference step size. */
        private static final float VELOCITY_DT = 0.01f;

    private BlobFlightManager() {}

    /**
     * Adds a new flight from the server's flight broadcast.
     * Called by BlobFlightHandler on packet receipt.
     *
     * @param payload the network payload
     */
    public static void addFlight(BlobFlightPayload payload) {
        GooType type = GooType.fromId(payload.gooTypeId());
        if (type == null) { return; }

        Vec3 start = new Vec3(payload.startX(), payload.startY(), payload.startZ());
        int targetEntityId = payload.targetEntityId();
        Vec3 blockEnd = (targetEntityId < 0) ? resolveBlockTargetPos(payload) : start;
        int travelTicks = payload.travelTicks();

        boolean grannyArc = payload.grannyArc();
        int id = nextId;
        nextId++;
        FLIGHTS.put(id, new BlobFlight(start, blockEnd, targetEntityId, type, travelTicks, grannyArc));
    }

    /** Called each client tick to advance flights and remove arrivals. */
    public static void tick() {
        Iterator<Map.Entry<Integer, BlobFlight>> it = FLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            BlobFlight flight = it.next().getValue();
            flight.ticksElapsed++;
            if (flight.ticksElapsed >= flight.travelTicks) {
                it.remove();
            }
        }
    }

    /**
     * Returns all active flights for rendering.
     *
     * @return the activeFlights
     */
    public static Collection<BlobFlight> getActiveFlights() {
        return FLIGHTS.values();
    }

    /** Clears all flights (on disconnect or dimension change). */
    public static void clear() {
        FLIGHTS.clear();
        nextId = 0;
    }

    /**
     * Resolves block face center from the payload.
     *
     * @param payload the network payload
     * @return the resolved result, or null if unresolvable
     */
    private static Vec3 resolveBlockTargetPos(BlobFlightPayload payload) {
        BlockPos pos = payload.targetPos();
        Direction face = (payload.targetFace() >= 0 && payload.targetFace() < Direction.values().length)
                ? Direction.values()[payload.targetFace()] : Direction.UP;
        return Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(FACE_CENTER_OFFSET));
    }

    /**
     * Resolves an entity's current center position, or null if gone.
     *
     * @param entityId the entityId identifier
     * @return the resolved result, or null if unresolvable
     */
    private static @Nullable Vec3 resolveEntityPos(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return null; }
        Entity e = mc.level.getEntity(entityId);
        if (e == null) { return null; }
        return e.position().add(0, e.getBbHeight() / ENTITY_CENTER_DIVISOR, 0);
    }

    /** Mutable flight state for a single blob in transit. */
    public static class BlobFlight {
        public final Vec3 start;
        /** Fixed end position for block targets; ignored for entity targets. */
        public final Vec3 blockEnd;
        /** Target entity ID, or -1 for block targets. */
        public final int targetEntityId;
        public final GooType gooType;
        public final int travelTicks;
        public final boolean grannyArc;
        public int ticksElapsed;

        public BlobFlight(Vec3 start, Vec3 blockEnd, int targetEntityId,
                          GooType gooType, int travelTicks, boolean grannyArc) {
            this.start = start;
            this.blockEnd = blockEnd;
            this.targetEntityId = targetEntityId;
            this.gooType = gooType;
            this.travelTicks = travelTicks;
            this.grannyArc = grannyArc;
            this.ticksElapsed = 0;
        }

        /**
         * Returns the current target position, tracking entity movement live.
         *
         * @return the end
         */
        public Vec3 getEnd() {
            if (targetEntityId >= 0) {
                Vec3 live = resolveEntityPos(targetEntityId);
                if (live != null) { return live; }
            }
            return blockEnd;
        }

        /**
         * Returns the arc peak height for this flight.
         *
         * @return the peak height in blocks above the start-end line
         */
        private double peak() {
            return grannyArc
                    ? ThrowArc.grannyPeak(travelTicks)
                    : ThrowArc.basePeak(travelTicks);
        }

        /**
         * Returns interpolated position at the given partial tick.
         *
         * @param partialTick the partial tick for interpolation
         * @return the position
         */
        public Vec3 getPosition(float partialTick) {
            float t = Math.min(1.0f, (ticksElapsed + partialTick) / travelTicks);
            return ThrowArc.arcPoint(start, getEnd(), t, peak());
        }

        /**
         * Returns normalized velocity direction for tail orientation.
         *
         * @param partialTick the partial tick for interpolation
         * @return the velocity
         */
        public Vec3 getVelocity(float partialTick) {
            float t = Math.min(1.0f, (ticksElapsed + partialTick) / travelTicks);
            Vec3 end = getEnd();
            double p = peak();
            Vec3 posNow = ThrowArc.arcPoint(start, end, t, p);
            Vec3 posNext = ThrowArc.arcPoint(start, end,
                    Math.min(1.0, t + VELOCITY_DT), p);
            Vec3 diff = posNext.subtract(posNow);
            double len = diff.length();
            return len > DIRECTION_EPSILON ? diff.scale(1.0 / len) : new Vec3(0, 1, 0);
        }
    }
}
