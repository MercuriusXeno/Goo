#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;

out vec2 quadUv;
out float progress;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // BER emits a billboard quad at local (±1, ±1, 0). Remap XY to [0, 1].
    quadUv = Position.xy * 0.5 + vec2(0.5);

    // Implosion progress is baked into Color.r on every vertex, so it
    // interpolates trivially (constant) to the fragment shader.
    progress = Color.r;
}
