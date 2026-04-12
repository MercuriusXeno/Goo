#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec2 faceUv;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));

    // CubeHoleStyle packs each vertex's intra-face UV into Color.rg:
    // (0,0) at one corner, (1,1) at the opposite corner, linearly
    // interpolated across the face. The fragment shader reads it to
    // compute distance to the nearest face edge without any ray math
    // or normal-based trickery.
    faceUv = Color.rg;
}
