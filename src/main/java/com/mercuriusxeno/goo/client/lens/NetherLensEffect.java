package com.mercuriusxeno.goo.client.lens;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;

/**
 * Screen-space gravitational-lensing post-process for nether black
 * holes. Applies the {@code goo:nether_lens} post-chain to the main
 * framebuffer when a nether black hole is active on the client, and
 * rewrites the chain's {@code LensConfig} uniform buffer each frame
 * with the current hole screen-space position and radii so the warp
 * tracks the hole as the camera (or the hole) moves.
 *
 * <p>Activation flow: {@link com.mercuriusxeno.goo.client.ber.NetherBlackHoleRender}
 * calls {@link #markHoleActive} from its extract path whenever it sees
 * an active {@code NetherBehavior}, recording the hole's world-space
 * center and effective radius. A client-side event handler drains that
 * state once per frame, projects the hole center into screen UV space
 * using the camera's orthonormal basis + the player's FOV setting
 * (bypassing the projection matrix so the lens works without an AT
 * for {@code GameRenderer.getFov}), writes fresh uniform bytes through
 * {@link CommandEncoder#writeToBuffer}, and toggles
 * {@link GameRenderer#setPostEffect} on or off accordingly.
 *
 * <p>The uniform update uses {@code writeToBuffer} rather than
 * {@code mapBuffer} because the chain's custom uniforms are created
 * with {@link GpuBuffer#USAGE_UNIFORM} only (no {@code MAP_WRITE}
 * bit). {@code writeToBuffer} is a device-side copy that works on any
 * buffer usage.
 */
public final class NetherLensEffect {

    /** Identifier of the post-effect chain at {@code assets/goo/post_effect/nether_lens.json}. */
    public static final Identifier POST_EFFECT_ID =
            Identifier.fromNamespaceAndPath(Goo.MODID, "nether_lens");

    /** UBO block name in {@code nether_lens.json} and {@code nether_lens.fsh}. */
    private static final String LENS_CONFIG_BLOCK = "LensConfig";

    /** Std140 size of the {@code LensConfig} uniform block. Two vec4s
     * at 16 bytes each. */
    private static final int LENS_CONFIG_SIZE = 32;

    /** Multiplier from event radius to the photon ring radius (the
     * characteristic bright ring just outside the horizon). 1.5 is the
     * Schwarzschild photon sphere radius in the low-budget Newtonian
     * approximation used by the shader. */
    private static final float PHOTON_RING_RADIUS_MULT = 1.5f;

    /** Base warp strength passed as {@code LensTuning.x}. Multiplies
     * the shader's {@code (eventRadius² / r²)} falloff, so the effective
     * max displacement right outside the horizon is {@code LENS_STRENGTH
     * × eventRadiusUv}. At 0.08, a hole occupying 0.2 of vertical UV
     * warps samples by up to 0.016 UV units at the rim — a visible but
     * not screen-eating bend. Previous value 0.6 was way too hot. */
    private static final float LENS_STRENGTH = 0.08f;

    /** Photon-ring additive brightness passed as {@code LensTuning.z}.
     * Dropped from 0.8 to 0.2 so the ring doesn't double up with the
     * forward-rendered corona BER pass (they occupy the same band
     * just outside the silhouette). */
    private static final float PHOTON_RING_BRIGHTNESS = 0.4f;

    /** Half-turn degrees; used to convert the FOV setting to radians. */
    private static final float DEG_TO_RAD = (float) Math.PI / 180f;

    /** Half-FOV scale: NDC Y spans [-1, 1] (a range of 2), UV spans
     * [0, 1] (a range of 1). Multiply by 0.5 to convert NDC deltas to
     * UV deltas. Also used as the half-NDC-to-UV offset for mapping
     * NDC center 0 to UV center 0.5. */
    private static final float NDC_TO_UV_SCALE = 0.5f;

    /** Half-NDC-range offset: added after {@link #NDC_TO_UV_SCALE} so
     * NDC 0 maps to UV 0.5. */
    private static final float NDC_TO_UV_OFFSET = 0.5f;

    /** Vertex label prefix for the LensConfig GpuBuffers we manage. */
    private static final String MANAGED_LABEL_PREFIX = "goo_nether_lens/";

    /** Staging vec4 reused across frames to avoid per-frame allocation. */
    private static final Vector4f HOLE_PARAMS = new Vector4f();
    private static final Vector4f LENS_TUNING = new Vector4f();

    /** World-space position of the active hole, or {@code null} if no
     * hole is currently active. Set by
     * {@link #markHoleActive(Vec3, float)}; cleared by the per-frame
     * update at the start of each frame if no BER marked one. */
    private static @Nullable Vec3 activeHoleCenter;
    /** Effective world-space radius of the active hole, in blocks. */
    private static float activeHoleRadius;
    /** Incremented every frame. Used to expire stale hole markers if
     * the BER path stops reporting one. */
    private static int markFrameStamp;
    /** Frame stamp at the last {@link #markHoleActive} call. */
    private static int lastMarkFrame = Integer.MIN_VALUE;

    /** The post chain instance whose LensConfig buffers have already
     * been swapped for writable copies. Null until we've seen a chain
     * for the first time. Used to detect resource reloads: if
     * {@link ShaderManager} hands back a different chain instance, we
     * rerun the swap so the new chain's fresh (immutable) buffers get
     * replaced too. */
    private static @Nullable PostChain managedChain;

    private NetherLensEffect() {}

    /** Reports an active nether black hole for this frame. Called by
     * the BER extract path when it sees a {@code NetherBehavior}.
     *
     * @param center      world-space center of the hole
     * @param worldRadius effective world-space radius in blocks
     */
    public static void markHoleActive(Vec3 center, float worldRadius) {
        activeHoleCenter = center;
        activeHoleRadius = worldRadius;
        lastMarkFrame = markFrameStamp;
    }

    /** Called once per frame before the post-effect pass runs. Advances
     * the frame stamp, projects the active hole (if any) into screen
     * UV space, writes uniforms into the lens pass's custom UBO, and
     * toggles the post effect on or off accordingly.
     *
     * @param mc the client instance
     */
    public static void applyPerFrame(Minecraft mc) {
        int frame = ++markFrameStamp;
        GameRenderer gameRenderer = mc.gameRenderer;
        // Stale if we didn't see a BER mark this frame or the last one
        // (tolerance for extract/event ordering jitter). If stale,
        // deactivate the post effect and bail out.
        boolean fresh = (frame - lastMarkFrame) <= 1 && activeHoleCenter != null;
        if (!fresh) {
            deactivateIfActive(gameRenderer);
            activeHoleCenter = null;
            return;
        }
        applyActiveHole(mc, gameRenderer);
    }

    /** Applies the currently-tracked hole: projects world→UV, loads the
     * chain, rewrites its uniforms, enables it.
     *
     * @param mc           the client instance
     * @param gameRenderer the active game renderer
     */
    private static void applyActiveHole(Minecraft mc, GameRenderer gameRenderer) {
        Vec3 center = activeHoleCenter;
        if (center == null) { return; }
        Camera camera = gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        // World-space displacement from camera to the hole center.
        double rx = center.x - cam.x;
        double ry = center.y - cam.y;
        double rz = center.z - cam.z;
        // Build the view-space position by dotting the displacement
        // against the camera's orthonormal basis. leftVector is the
        // screen-left unit vector, so the conventional "right" axis
        // is its negation. Forward is into the scene (+Z view).
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        float vForward = (float) (forward.x() * rx + forward.y() * ry + forward.z() * rz);
        if (vForward <= 0f) {
            // Behind the camera; no on-screen position. Disable this
            // frame and bail.
            deactivateIfActive(gameRenderer);
            return;
        }
        float vUp = (float) (up.x() * rx + up.y() * ry + up.z() * rz);
        float vRight = (float) -(left.x() * rx + left.y() * ry + left.z() * rz);
        // Perspective divide with the player's current vertical FOV.
        // mc.options.fov() is in degrees and covers the vertical axis
        // of the viewport; aspect scales the horizontal axis. This
        // matches Mojang's getFov/getProjectionMatrix construction
        // closely enough for the lens warp's purposes, ignoring
        // transient effects like bow-pull zoom modifiers.
        int fovDeg = mc.options.fov().get();
        float halfFovRad = fovDeg * NDC_TO_UV_SCALE * DEG_TO_RAD;
        float tanHalfFov = (float) Math.tan(halfFovRad);
        int wPx = mc.getWindow().getWidth();
        int hPx = Math.max(1, mc.getWindow().getHeight());
        float aspect = (float) wPx / hPx;
        float ndcX = vRight / (vForward * tanHalfFov * aspect);
        float ndcY = vUp / (vForward * tanHalfFov);
        float uvX = ndcX * NDC_TO_UV_SCALE + NDC_TO_UV_OFFSET;
        float uvY = ndcY * NDC_TO_UV_SCALE + NDC_TO_UV_OFFSET;
        // Angular radius in UV (vertical) units: a sphere of
        // world-space radius R at forward distance D subtends
        // ~ R / D radians of vertical visual angle. Divide by
        // (forward * tanHalfFov) to get a radius in vertical NDC
        // units ([-1, 1]), then multiply by NDC_TO_UV_SCALE to convert
        // to UV units ([0, 1]) since UV spans half the NDC range.
        float eventRadiusUv = (activeHoleRadius / (vForward * tanHalfFov)) * NDC_TO_UV_SCALE;
        if (eventRadiusUv <= 0f) {
            deactivateIfActive(gameRenderer);
            return;
        }
        float photonRadiusUv = eventRadiusUv * PHOTON_RING_RADIUS_MULT;
        HOLE_PARAMS.set(uvX, uvY, eventRadiusUv, photonRadiusUv);
        LENS_TUNING.set(LENS_STRENGTH, aspect, PHOTON_RING_BRIGHTNESS, 0f);
        PostChain chain = loadChain(mc);
        if (chain == null) {
            // Chain didn't compile this frame (resource reload race
            // or shader error). Leave the post effect alone.
            return;
        }
        writeUniforms(chain, HOLE_PARAMS, LENS_TUNING);
        activateIfInactive(gameRenderer);
    }

    /** Clears the active hole state and disables the post effect so the
     * sentinel HoleParams (-1) short-circuits the shader on any stale
     * render calls before the post effect is fully unhooked.
     *
     * @param gameRenderer the active game renderer
     */
    private static void deactivateIfActive(GameRenderer gameRenderer) {
        if (!POST_EFFECT_ID.equals(gameRenderer.currentPostEffect())) {
            return;
        }
        gameRenderer.clearPostEffect();
    }

    /** Activates the post effect if the slot is free or already ours.
     * Yields to any other post effect (vanilla spider/creeper/enderman
     * spectator cams, or another mod's chain) — {@link GameRenderer}
     * only has one post-effect slot and we'd rather skip the lens
     * this frame than stomp somebody else's. Safe to call every frame;
     * on the frame the hole first becomes visible and the slot is
     * free, this triggers the state change, and otherwise does
     * nothing.
     *
     * @param gameRenderer the active game renderer
     */
    private static void activateIfInactive(GameRenderer gameRenderer) {
        Identifier current = gameRenderer.currentPostEffect();
        if (POST_EFFECT_ID.equals(current)) {
            return;
        }
        if (current != null) {
            // Something else owns the slot (e.g. spectating a creeper).
            // Yield this frame rather than clobber it.
            return;
        }
        gameRenderer.setPostEffect(POST_EFFECT_ID);
    }

    /** Looks up the lens post-chain via the shader manager. The chain
     * is cached by {@link ShaderManager} so repeat calls are cheap.
     *
     * @param mc the client instance
     * @return the loaded chain or {@code null} if compilation failed
     */
    private static @Nullable PostChain loadChain(Minecraft mc) {
        ShaderManager sm = mc.getShaderManager();
        return sm.getPostChain(POST_EFFECT_ID, LevelTargetBundle.MAIN_TARGETS);
    }

    /** Rewrites the {@code LensConfig} uniform buffer on every pass in
     * the chain that declares it. {@link PostChain#passes} and
     * {@link PostPass#customUniforms} are both exposed via access
     * transformers in {@code META-INF/accesstransformer.cfg}.
     *
     * <p>PostPass creates its custom-uniform buffers with plain
     * {@link GpuBuffer#USAGE_UNIFORM}, which is not a valid
     * {@code writeToBuffer} destination (the OpenGL backend requires
     * {@link GpuBuffer#USAGE_COPY_DST}). On the first frame we see a
     * given chain, we replace those immutable buffers with writable
     * ones we own ({@code USAGE_UNIFORM | USAGE_COPY_DST}) and let the
     * initial data carry the first frame's payload. On every
     * subsequent frame we just stream new bytes through
     * {@code writeToBuffer}, which is cheap.
     *
     * @param chain      the loaded post chain
     * @param holeParams HoleParams vec4 to write
     * @param lensTuning LensTuning vec4 to write
     */
    private static void writeUniforms(PostChain chain, Vector4f holeParams, Vector4f lensTuning) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, LENS_CONFIG_SIZE);
            builder.putVec4(holeParams);
            builder.putVec4(lensTuning);
            ByteBuffer bytes = builder.get();
            if (managedChain != chain) {
                // First sighting of this chain: swap out the
                // immutable USAGE_UNIFORM buffers and install the
                // initial data directly. The swap installs writable
                // buffers so subsequent frames can writeToBuffer.
                installManagedBuffers(chain, bytes);
                managedChain = chain;
                return;
            }
            // Subsequent frames: stream new bytes through the existing
            // writable buffers. Rewind between passes so each sees the
            // full payload.
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (PostPass pass : chain.passes) {
                GpuBuffer buffer = pass.customUniforms.get(LENS_CONFIG_BLOCK);
                if (buffer == null) { continue; }
                bytes.position(0);
                encoder.writeToBuffer(buffer.slice(), bytes);
            }
        }
    }

    /** Replaces each pass's {@code LensConfig} GpuBuffer with a fresh
     * one that carries the {@code USAGE_COPY_DST} bit, using
     * {@code initialData} as the first frame's payload. The old
     * (PostPass-created) buffer is closed so its GPU memory is
     * released.
     *
     * @param chain       the post chain whose buffers to swap
     * @param initialData the std140 bytes for the first frame
     */
    private static void installManagedBuffers(PostChain chain, ByteBuffer initialData) {
        int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        for (PostPass pass : chain.passes) {
            GpuBuffer oldBuffer = pass.customUniforms.get(LENS_CONFIG_BLOCK);
            if (oldBuffer == null) { continue; }
            initialData.position(0);
            GpuBuffer newBuffer = RenderSystem.getDevice().createBuffer(
                    () -> MANAGED_LABEL_PREFIX + LENS_CONFIG_BLOCK,
                    usage,
                    initialData);
            pass.customUniforms.put(LENS_CONFIG_BLOCK, newBuffer);
            oldBuffer.close();
        }
    }
}
