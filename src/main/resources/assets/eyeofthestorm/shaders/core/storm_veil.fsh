#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float MorphCycle;
uniform float SliceCount;
uniform float VeilContrast;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float count = max(SliceCount, 2.0);
    float cycleSec = max(MorphCycle, 0.1);
    float phase = mod(GameTime * (1200.0 / cycleSec), 1.0);
    float fIndex = phase * count;
    float i0 = floor(fIndex);
    float i1 = mod(i0 + 1.0, count);
    float blend = fract(fIndex);
    float inv = 1.0 / count;

    // Tile Voronoi within the active morph slices (no discard — soft alpha only).
    float u = fract(texCoord0.x);
    float v = fract(texCoord0.y);
    vec4 texA = texture(Sampler0, vec2(u, (i0 + v) * inv));
    vec4 texB = texture(Sampler0, vec2(u, (i1 + v) * inv));
    float density = mix(texA.a, texB.a, blend);

    // Flat wash + clearer density modulation (still no hard cutouts).
    float contrast = clamp(VeilContrast, 0.0, 1.0);
    float shaped = mix(1.0, density, contrast);
    shaped = pow(max(shaped, 1e-3), mix(1.0, 0.7, contrast));
    // Floor keeps interiors tinted; lower floor = more obvious cells.
    float veil = mix(0.38, 1.0, shaped);

    vec3 rgb = vertexColor.rgb * ColorModulator.rgb;
    // Veins read hotter / interiors a touch darker so the pattern pops.
    rgb *= mix(0.62, 1.15, shaped);
    float a = vertexColor.a * ColorModulator.a * veil;
    fragColor = vec4(rgb, clamp(a, 0.0, 1.0));
}
