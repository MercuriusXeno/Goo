#version 330

// Nether black-hole gravitational-lensing post-process.
//
// Samples the main framebuffer and warps the sample coordinate toward
// the black hole's screen-space center, producing the characteristic
// double-rim halo (the far half of the accretion disc folds over the
// top and bottom of the event horizon). Inside the event-horizon
// radius, the fragment is forced to pure black so the lensed scene
// blends smoothly into the occluding sphere that the BER draws in its
// own forward pass.
//
// Warp model: Newtonian bend, strength ~ 1 / dist. Visually matches
// Schwarzschild deflection at this brightness budget without the
// expense of raymarching. Everything past the warp region is passed
// through unchanged, so the effect is free outside its radius.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    // (holeUvX, holeUvY, eventHorizonUvRadius, photonRingUvRadius)
    // Hole center and characteristic radii in UV space. A negative X
    // value is used as a sentinel for "no active hole", in which case
    // the shader short-circuits to a pass-through blit.
    vec4 HoleParams;
    // (lensStrength, aspectRatio, ringBrightness, _)
    // lensStrength scales the warp magnitude; aspect corrects the
    // distance metric so the hole looks circular on non-square
    // viewports; ringBrightness is the photon-sphere additive boost.
    vec4 LensTuning;
};

out vec4 fragColor;

// Edge softness for the event-horizon occluder, in UV units.
const float HORIZON_AA = 0.002;

// Sharpness of the photon-ring highlight. Higher = thinner ring.
// Raised from 90 to 600 so the ring is a crisp filament sitting right
// outside the silhouette instead of a fat blurry halo competing with
// the forward-rendered corona pass.
const float RING_SHARPNESS = 180.0;

void main() {
    vec2 holeUv = HoleParams.xy;
    float eventRadius = HoleParams.z;
    float photonRadius = HoleParams.w;
    float lensStrength = LensTuning.x;
    float aspect = LensTuning.y;
    float ringBoost = LensTuning.z;

    // Sentinel: hole X < 0 means "no active hole". Pass through.
    if (holeUv.x < 0.0 || eventRadius <= 0.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    // Aspect-correct offset so the hole is circular on screen even on
    // wide viewports. Stretch X by aspect ratio so the distance metric
    // is in a square space before we compute lengths / warps.
    vec2 d = texCoord - holeUv;
    vec2 dAspect = vec2(d.x * aspect, d.y);
    float r = length(dAspect);

    // Inside the event horizon: pure black, with a soft AA edge so the
    // silhouette blends cleanly into the BER's occluding sphere.
    if (r < eventRadius) {
        float t = smoothstep(eventRadius - HORIZON_AA, eventRadius, r);
        vec2 ringUv = texCoord + vec2(0.0, 0.0);  // no warp at the horizon edge
        vec4 edge = texture(InSampler, ringUv);
        fragColor = mix(vec4(0.0, 0.0, 0.0, 1.0), edge, t);
        return;
    }

    // Gravitational warp: displacement toward the hole center with
    // magnitude ~ eventRadius^2 / r (Newtonian, strongest right at the
    // horizon and falling off outside). Unit direction is the screen-
    // space vector from the fragment to the hole center, undoing the
    // aspect stretch so the warp lands on the right texel.
    vec2 dirAspect = -dAspect / max(r, 1e-5);
    vec2 dir = vec2(dirAspect.x / aspect, dirAspect.y);
    float falloff = (eventRadius * eventRadius) / (r * r);
    float warpMag = falloff * lensStrength;
    vec2 warpedUv = texCoord + dir * warpMag;
    // Clamp into the valid sampler range so lensing off the edge of
    // the frame doesn't wrap around or read garbage.
    warpedUv = clamp(warpedUv, vec2(0.0), vec2(1.0));

    vec4 warped = texture(InSampler, warpedUv);

    // Photon ring: a thin additive brightness boost at the critical
    // photon sphere. Gaussian centered on photonRadius, width set by
    // RING_SHARPNESS. Adds an emissive rim right outside the horizon
    // even if the underlying lensed color is dark.
    float ringDelta = r - photonRadius;
    float ring = exp(-RING_SHARPNESS * ringDelta * ringDelta) * ringBoost;
    vec3 ringColor = vec3(1.0, 0.85, 0.55);

    fragColor = vec4(warped.rgb + ringColor * ring, 1.0);
}
