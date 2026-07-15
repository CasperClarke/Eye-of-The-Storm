#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float MorphCycle;
uniform float SliceCount;
uniform vec2 StormCenterRel;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewPosition;

out vec4 fragColor;

void main() {
    float count = max(SliceCount, 2.0);
    // MorphCycle is real seconds. Vanilla GameTime is 0..1 over one 24000-tick day (1200s).
    float cycleSec = max(MorphCycle, 0.1);
    float phase = mod(GameTime * (1200.0 / cycleSec), 1.0);
    float fIndex = phase * count;
    float i0 = floor(fIndex);
    float i1 = mod(i0 + 1.0, count);
    float sliceBlend = fract(fIndex);

    // World-space UVs repeat; atlas slices are stacked in V (use local 0..1 tile coords).
    float invCount = 1.0 / count;
    float vLocal = fract(texCoord0.y);
    float uLocal = fract(texCoord0.x);
    vec2 uvA = vec2(uLocal, (i0 + vLocal) * invCount);
    vec2 uvB = vec2(uLocal, (i1 + vLocal) * invCount);

    vec4 texA = texture(Sampler0, uvA);
    vec4 texB = texture(Sampler0, uvB);
    vec4 sampled = mix(texA, texB, sliceBlend);
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
    color.a = min(color.a, 1.0);

    float pulse = 0.97 + 0.03 * sin(GameTime * 1200.0 * 0.55);
    color.a *= pulse;

    fragColor = color;
}
