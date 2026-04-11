#version 330

out vec4 fragColor;

// Solid near-black core. The sphere's only job in this pass is to act
// as an opaque occluder that hides the destroyed volume inside it; the
// visible "rim" comes from the separate corona pass in nether_corona.fsh.
const vec3 CORE_COLOR = vec3(0.01, 0.00, 0.03);

void main() {
    fragColor = vec4(CORE_COLOR, 1.0);
}
