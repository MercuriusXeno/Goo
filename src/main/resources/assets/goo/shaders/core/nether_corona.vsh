#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 centerView;
out float mainRadiusSq;

// Must match ChainMarkerBER.CORONA_SCALE exactly. The corona mesh is the
// unit sphere rendered at this scale relative to the main black-hole
// sphere, so if this changes the BER constant must change with it.
const float CORONA_SCALE = 1.15;

// Must match ChainMarkerBER.MAX_ENCODED_RADIUS exactly. The BER packs
// {@code visibleRadius / MAX_ENCODED_RADIUS} into the vertex Color.b
// byte; we decode by multiplying back. 16 is comfortably above the max
// actual visible radius (~11 blocks for stack-4 nether).
const float MAX_ENCODED_RADIUS = 16.0;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;

    // Decode the main sphere's visible radius (in world blocks) from
    // the blue channel of the vertex color.
    float mainRadius = Color.b * MAX_ENCODED_RADIUS;
    mainRadiusSq = mainRadius * mainRadius;

    // Recover the sphere center in view space using the known
    // relationship Position = center + coronaRadius * Normal. Transform
    // the view-space normal and subtract coronaRadius * viewNormal from
    // the view-space position to get the view-space center.
    vec3 viewNormal = normalize((ModelViewMat * vec4(Normal, 0.0)).xyz);
    float coronaRadius = mainRadius * CORONA_SCALE;
    centerView = vp.xyz - coronaRadius * viewNormal;
}
