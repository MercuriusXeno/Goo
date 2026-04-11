#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewNormal;
out vec3 worldNormal;
out float progress;
out float animationTime;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    // Transform the unit-sphere normal into view space so the fragment
    // shader can compute a fresnel factor (|viewNormal.z| == 1 at the
    // center facing the camera, == 0 at the silhouette).
    viewNormal = normalize((ModelViewMat * vec4(Normal, 0.0)).xyz);

    // Pass the object/world-space normal through unmodified for the
    // fragment shader's swirl angle. The BER pose for the blackhole
    // sphere is translation-only (no rotation or scale), so the input
    // Normal attribute is already the world-space unit direction from
    // the sphere center to the vertex. Using this for the swirl angle
    // anchors the pattern to the world - walking around the sphere no
    // longer rotates the bands with the camera.
    worldNormal = normalize(Normal);

    // Color.r carries the implosion progress (visible scale 0..1).
    // Color.g carries the BE's animation phase (0..1 cycling) so the
    // swirl rotates deterministically regardless of GameTime plumbing.
    progress = Color.r;
    animationTime = Color.g;
}
