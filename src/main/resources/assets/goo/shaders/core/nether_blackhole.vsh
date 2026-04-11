#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 quadUv;
out float progress;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // BER emits explicit UVs spanning [0, 1] across the quad so the billboard
    // can be constructed in world-space coordinates directly, without relying
    // on mulPose(camera.orientation).
    quadUv = UV0;

    // Implosion progress is baked into Color.r on every vertex.
    progress = Color.r;
}
