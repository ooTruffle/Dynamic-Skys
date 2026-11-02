// Void Rift: a glowing tear across the sky, churning with energy.

vec3 sky(vec3 d) {
    vec3 col = vec3(0.005, 0.0, 0.012);
    col += stars(d, 220.0, 0.92) * 0.8;

    vec3 n = normalize(vec3(0.3, 0.25, 1.0));
    float warp = (fbm3(d * 3.0 + vec3(0.0, uTime * 0.05, 0.0)) - 0.5) * 0.35;
    float dist = abs(dot(d, n) + warp);
    float width = 0.05 * (1.0 + uBass * 0.8) * (0.6 + 0.8 * fbm(ring(d, 3.0)));

    float inside = smoothstep(width, width * 0.4, dist);
    float edge = exp(-abs(dist - width * 0.7) * 50.0);
    float energy = fbm(d * 6.0 + vec3(uTime * 0.2, -uTime * 0.1, 0.0));

    vec3 core = mix(vec3(0.4, 0.0, 0.8), vec3(0.1, 0.9, 1.0), energy);
    col = mix(col, core * (0.8 + energy), inside);
    col += vec3(0.8, 0.4, 1.0) * edge * (1.0 + uBeat);
    col += vec3(0.35, 0.05, 0.6) * exp(-dist * 9.0) * (0.4 + uLevel * 0.6);
    return col;
}
