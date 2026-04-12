#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float radialT;
out float angularT;
out float animPhase;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));

    // Per-vertex disc coordinates packed by NetherBlackHoleRender:
    //   Color.r = radialT  - 0 at the inner edge (just outside the
    //                         sphere's silhouette), 1 at the outer edge
    //                         of the swept disc. Drives the radial
    //                         brightness curve and the inner-to-outer
    //                         color ramp.
    //   Color.g = angularT - 0..1 parametric angle around the ring,
    //                         drives the swirl band lookups.
    //   Color.b = animPhase - 0..1 global animation phase, rotates the
    //                         swirl pattern over time.
    // There is no minor-angle term here: brightness is radial-only, so
    // the same falloff reads from any viewing angle.
    radialT = Color.r;
    angularT = Color.g;
    animPhase = Color.b;
}
