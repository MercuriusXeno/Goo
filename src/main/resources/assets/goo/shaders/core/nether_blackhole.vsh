#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewNormal;
out float progress;
out float animationTime;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    // Transform the unit-sphere normal into view space. The fragment
    // shader uses it for both the fresnel factor (|viewNormal.z| == 1
    // at the center facing the camera, == 0 at the silhouette) and the
    // roll angle around the view axis, which together define a vortex
    // centered on whichever point of the sphere is facing the camera.
    viewNormal = normalize((ModelViewMat * vec4(Normal, 0.0)).xyz);

    // Color.r carries the implosion progress (visible scale 0..1).
    // Color.g carries the BE's animation phase (0..1 cycling) so the
    // swirl rotates deterministically regardless of GameTime plumbing.
    progress = Color.r;
    animationTime = Color.g;
}
