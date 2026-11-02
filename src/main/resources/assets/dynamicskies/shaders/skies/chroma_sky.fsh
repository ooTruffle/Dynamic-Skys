// Chroma Sky: rolling rainbow waves of color.

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x) / TAU;
    float w = fbm(ring(d, 2.0) + vec2(d.y * 2.0, uTime * 0.05));
    float hue = az + d.y * 0.4 + uTime * 0.04 + w * 0.35 + uMid * 0.1;
    float wave = 0.5 + 0.5 * sin(d.y * 12.0 - uTime * 0.8 + w * 6.0);
    float sat = 0.55 + 0.25 * wave + uLevel * 0.15;
    vec3 col = hsv2rgb(vec3(hue, clamp(sat, 0.0, 1.0), 0.65 + 0.3 * wave));
    col *= 0.8 + uBeat * 0.4;
    return col;
}
