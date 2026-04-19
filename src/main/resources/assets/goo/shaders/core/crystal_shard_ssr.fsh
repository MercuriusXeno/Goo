#version 330

in vec4 vertColor;
in vec3 reflectedColor;

out vec4 fragColor;

// Base opacity
const float BASE_ALPHA = 0.8;

void main() {
    float density = vertColor.r;
    float animFade = vertColor.g;
    float alpha = BASE_ALPHA * max(density, 0.2) * animFade;

    fragColor = vec4(reflectedColor, alpha);
}
