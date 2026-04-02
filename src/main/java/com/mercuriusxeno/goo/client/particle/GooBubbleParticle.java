package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.CrucibleParticleHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Color-tinted bubble particle that spawns on the crucible liquid surface.
 * Lifecycle: TENSION (0-3), EXPAND (4-13), LINGER (14-33), DANCE (34-53), POP (54-57).
 * Tension shows frame 0 (surface break), then the dome (frame 3) scales from 40% to full.
 */
public class GooBubbleParticle extends SingleQuadParticle {

    /** End of expansion (frames 0-2 for first 3 ticks, dome for remaining 7). */
    private static final int EXPAND_END = 10;
    private static final int LINGER_END = 30;
    private static final int DANCE_END = 50;
    private static final int POP_END = 54;
    /** Number of emergence frames before dome (0: tension, 1: squat, 2: half). */
    private static final int EMERGE_FRAMES = 3;
    /** Dome sprite index. */
    private static final int DOME_FRAME = 3;
    /** First pop sprite index. */
    private static final int POP_FRAME_START = 4;
    /** Total sprite count. */
    private static final int TOTAL_FRAMES = 8;
    /** Number of pop sprite frames (4-7). */
    private static final int POP_FRAMES = TOTAL_FRAMES - POP_FRAME_START;
    /** Horizontal drift speed during DANCE phase (blocks/tick). */
    private static final double DRIFT_SPEED = 0.0015;
    /** Scale factor at the start of dome expansion. */
    private static final float SMALL_SCALE = 0.4f;

    /** Sprite set for cycling through animation frames. */
    private final SpriteSet sprites;
    /** Source crucible position for goo-presence checks. */
    private final BlockPos sourcePos;

    /** Persistent drift velocities for smooth gliding during DANCE. */
    private double driftX;
    private double driftZ;

    /** Creates a goo bubble particle at the liquid surface with the given color tint. */
    private GooBubbleParticle(ClientLevel level, double x, double y, double z,
            float red, float green, float blue, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.sourcePos = BlockPos.containing(x, y, z);
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
        this.lifetime = POP_END;
        this.quadSize = 0.06f;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.driftX = 0;
        this.driftZ = 0;
        this.hasPhysics = false;
    }

    /** Goo bubbles render on the opaque particle layer. */
    @Override
    public Layer getLayer() {
        return Layer.OPAQUE;
    }

    /**
     * Returns quad size. During EXPAND, scales continuously from 40% to 100%.
     * Frames 0-2 show during the first 3 ticks, then dome takes over.
     */
    @Override
    public float getQuadSize(float partialTick) {
        if (age >= EXPAND_END) return this.quadSize;
        float t = (age + partialTick) / EXPAND_END;
        t = Math.min(1f, Math.max(0f, t));
        return this.quadSize * (SMALL_SCALE + (1f - SMALL_SCALE) * t);
    }

    /**
     * Updates particle sprite based on the current lifecycle phase.
     * Position is fixed at spawn; only DANCE adds gentle horizontal drift.
     */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age >= this.lifetime) {
            this.remove();
            return;
        }

        if (tickSourceCrucible()) {
            skipToPop();
            this.age++;
            return;
        }

        if (age < EXPAND_END) {
            tickExpand();
        } else if (age < LINGER_END) {
            tickLinger();
        } else if (age < DANCE_END) {
            tickDance();
        } else {
            tickPop();
        }

        this.age++;
    }

    /** EXPAND: frames 0-2 for first 3 ticks, then dome for remaining 7. */
    private void tickExpand() {
        int frame = (age < EMERGE_FRAMES) ? age : DOME_FRAME;
        this.setSprite(this.sprites.get(frame, TOTAL_FRAMES - 1));
    }

    /** LINGER: holds dome sprite at full size, no movement. */
    private void tickLinger() {
        this.setSprite(this.sprites.get(DOME_FRAME, TOTAL_FRAMES - 1));
    }

    /** DANCE: glide smoothly with gentle drift, showing dome sprite. */
    private void tickDance() {
        this.setSprite(this.sprites.get(DOME_FRAME, TOTAL_FRAMES - 1));
        if ((this.age - LINGER_END) % 8 == 0) {
            pickNewDrift();
        }
        this.x += driftX;
        this.z += driftZ;
    }

    /** Picks a new random drift direction for smooth surface gliding. */
    private void pickNewDrift() {
        this.driftX = (this.random.nextDouble() - 0.5) * DRIFT_SPEED;
        this.driftZ = (this.random.nextDouble() - 0.5) * DRIFT_SPEED;
    }

    /**
     * Queries the source crucible once per tick: pops early if goo is gone,
     * otherwise tracks the liquid surface Y. Returns true if the bubble should pop.
     */
    private boolean tickSourceCrucible() {
        if (age >= DANCE_END) return false;
        BlockEntity be = this.level.getBlockEntity(sourcePos);
        if (!(be instanceof CrucibleBlockEntity crucible)) return true;
        long total = crucible.getReservoir().totalVolume()
                + crucible.getPoolVolume();
        if (total <= 0) return true;
        float surfaceY = CrucibleParticleHelper.computeSurfaceY(total);
        this.y = sourcePos.getY() + surfaceY + 1.0 / 32.0;
        return false;
    }

    /** Jumps to the POP phase and sets lifetime so the pop animation plays out. */
    private void skipToPop() {
        this.age = DANCE_END;
        this.lifetime = POP_END;
        tickPop();
    }

    /** POP: cycles through pop sprite frames (4-7). */
    private void tickPop() {
        int popTick = this.age - DANCE_END;
        int frame = POP_FRAME_START + popTick * POP_FRAMES / (POP_END - DANCE_END);
        this.setSprite(this.sprites.get(frame, TOTAL_FRAMES - 1));
    }

    /**
     * Provider that creates GooBubbleParticles from ColorParticleOption data.
     * Registered via RegisterParticleProvidersEvent with a SpriteSet.
     */
    public static class Provider implements ParticleProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        /** Creates a provider with the given sprite set from the particle definition. */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /** Creates a goo bubble particle, extracting RGB from the color option. */
        @Override
        public @Nullable GooBubbleParticle createParticle(
                ColorParticleOption options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new GooBubbleParticle(level, x, y, z,
                options.getRed(), options.getGreen(), options.getBlue(), sprites);
        }
    }
}
