#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
}
