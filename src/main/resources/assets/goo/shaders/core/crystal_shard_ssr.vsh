#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D SceneSampler;

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec4 vertColor;
out vec3 reflectedColor;

// How far the reflection sample jumps across the screen (in UV space)
const float REFLECT_STRENGTH = 0.35;
// Brightness boost
const float BRIGHTNESS = 1.2;
// Chromatic aberration offset
const float CHROMA_OFFSET = 0.003;
// NDC to UV conversion
const float NDC_TO_UV = 0.5;
// Blur kernel radius in pixels - larger = more stable, softer reflections
const float BLUR_RADIUS = 6.0;

// 9-tap box blur: averages a neighborhood to stabilize the sample
vec3 sampleBlurred(vec2 uv, vec2 pixelSize) {
    vec3 c = texture(SceneSampler, uv).rgb;
    c += texture(SceneSampler, clamp(uv + vec2(-pixelSize.x, -pixelSize.y), 0.0, 1.0)).rgb;
    c += texture(SceneSampler, clamp(uv + vec2( 0.0,         -pixelSize.y), 0.0, 1.0)).rgb;
    c += texture(SceneSampler, clamp(uv + vec2( pixelSize.x, -pixelSize.y), 0.0, 1.0)).rgb;
    c += texture(SceneSampler, clamp(uv + vec2(-pixelSize.x,  0.0),         0.0, 1.0)).rgb;
    c += texture(SceneSampler, clamp(uv + vec2( pixelSize.x,  0.0),         0.0, 1.0)).rgb;
    c += texture(SceneSampler, clamp(uv + vec2(-pixelSize.x,  pixelSize.y), 0.0, 1.0)).rgb;
    c += texture(SceneSampler, clamp(uv + vec2( 0.0,          pixelSize.y), 0.0, 1.0)).rgb;
    c += texture(SceneSampler, clamp(uv + vec2( pixelSize.x,  pixelSize.y), 0.0, 1.0)).rgb;
    return c / 9.0;
}

void main() {
    vec4 clipPos = ProjMat * (ModelViewMat * vec4(Position, 1.0));
    gl_Position = clipPos;
    vertColor = Color;

    // Screen UV from projected position
    vec2 screenUv = clipPos.xy / clipPos.w * NDC_TO_UV + NDC_TO_UV;
    vec2 pixelSize = BLUR_RADIUS / ScreenSize;

    // Reflect view direction off vertex normal
    vec3 viewNormal = normalize((ModelViewMat * vec4(Normal, 0.0)).xyz);
    vec3 refl = reflect(vec3(0.0, 0.0, -1.0), viewNormal);

    // Reflected sample position
    vec2 sampleUv = clamp(screenUv + refl.xy * REFLECT_STRENGTH, 0.0, 1.0);

    // Chromatic aberration on blurred samples
    vec2 chromaDir = normalize(refl.xy + 0.001) * CHROMA_OFFSET;
    float r = sampleBlurred(clamp(sampleUv + chromaDir, 0.0, 1.0), pixelSize).r;
    float g = sampleBlurred(sampleUv, pixelSize).g;
    float b = sampleBlurred(clamp(sampleUv - chromaDir, 0.0, 1.0), pixelSize).b;

    reflectedColor = vec3(r, g, b) * BRIGHTNESS;
}
