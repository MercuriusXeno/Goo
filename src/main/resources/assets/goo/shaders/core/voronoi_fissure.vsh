#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 worldPos;
out vec4 vertColor;
out vec3 unitNormal;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
    worldPos = Position;
    vertColor = Color;
    unitNormal = Normal;
}
