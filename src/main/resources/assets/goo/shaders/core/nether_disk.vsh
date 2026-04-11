#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float innerness;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));

    // The BER packs a per-vertex "innerness" parameter into Color.r:
    //   0.0 at the outer edge of the torus tube (dimmest)
    //   1.0 at the inner edge touching the sphere equator (brightest)
    //   0.5 at the top and bottom of the tube (Y-extrusion, feathering)
    // Derived from the tube's minor angle phi as (1 - cos(phi)) / 2, so
    // the value correctly interpolates the ring's cross-section.
    innerness = Color.r;
}
