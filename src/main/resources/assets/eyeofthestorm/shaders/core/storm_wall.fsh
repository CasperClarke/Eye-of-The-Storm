#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float MorphCycle;
uniform float SliceCount;
uniform vec2 StormCenterRel;
uniform vec2 ScreenSize;
uniform float NearPlane;
uniform float FarPlane;
uniform float ContactWidth;
uniform float ContactStrength;
uniform float ContactEnabled;
uniform float CloudContactEnabled;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewPosition;

out vec4 fragColor;

/** Sample the morph atlas at UV + morph phase (0..1 through the W-slice ring). */
vec4 sampleMorph(float u, float v, float phase01) {
    float count = max(SliceCount, 2.0);
    float fIndex = fract(phase01) * count;
    float i0 = floor(fIndex);
    float i1 = mod(i0 + 1.0, count);
    float blend = fract(fIndex);
    float invCount = 1.0 / count;
    float uLocal = fract(u);
    float vLocal = fract(v);
    vec4 texA = texture(Sampler0, vec2(uLocal, (i0 + vLocal) * invCount));
    vec4 texB = texture(Sampler0, vec2(uLocal, (i1 + vLocal) * invCount));
    return mix(texA, texB, blend);
}

/** Window depth (0..1) → positive eye-space distance. */
float linearEyeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    float n = max(NearPlane, 0.01);
    float f = max(FarPlane, n + 1.0);
    return (2.0 * n * f) / (f + n - z * (f - n));
}

void main() {
    float cycleSec = max(MorphCycle, 0.1);
    // MorphCycle is real seconds. Vanilla GameTime is 0..1 over one 24000-tick day (1200s).
    float phase = mod(GameTime * (1200.0 / cycleSec), 1.0);

    vec4 sampled = sampleMorph(texCoord0.x, texCoord0.y, phase);
    vec4 color = sampled * vertexColor * ColorModulator;

    vec2 outward = viewPosition.xz - StormCenterRel;
    float oLen = length(outward);
    vec3 N = oLen > 1e-4
        ? normalize(vec3(outward.x, 0.0, outward.y))
        : vec3(0.0, 0.0, 1.0);
    vec3 V = normalize(-viewPosition);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fresnel = pow(1.0 - ndv, 2.0);

    color.rgb *= mix(1.0, 0.30, fresnel);
    color.a *= mix(1.0, 1.75, fresnel);

    // Soft contact rim: wall depth vs scene depth (terrain, caves, Fancy clouds).
    // Fabulous clouds live in Sampler2 — take the closer eye depth.
    // Scale by vertexColor.a so the rim follows the same fog / top-cap fade as the wall.
    if (ContactEnabled > 0.5) {
        float visibility = clamp(vertexColor.a, 0.0, 1.0);
        vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
        float sceneEye = linearEyeDepth(texture(Sampler1, screenUv).r);
        if (CloudContactEnabled > 0.5) {
            float cloudEye = linearEyeDepth(texture(Sampler2, screenUv).r);
            sceneEye = min(sceneEye, cloudEye);
        }
        float wallEye = linearEyeDepth(gl_FragCoord.z);
        float gap = abs(sceneEye - wallEye);
        float contact = 1.0 - smoothstep(0.0, max(ContactWidth, 0.05), gap);
        contact *= contact;
        float glow = contact * max(ContactStrength, 0.0) * visibility;
        color.rgb += vec3(1.0, 0.18, 0.06) * glow;
        color.a = min(1.0, color.a + glow * 0.75);
    }

    color.a = min(color.a, 1.0);

    float pulse = 0.97 + 0.03 * sin(GameTime * 1200.0 * 0.55);
    color.a *= pulse;

    fragColor = color;
}
