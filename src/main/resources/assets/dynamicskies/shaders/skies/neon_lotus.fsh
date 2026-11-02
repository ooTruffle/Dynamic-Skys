// Neon Lotus: a giant glowing lotus blooming overhead, its petals drawn in neon light.

float petalLine(vec2 p, float count, float size, float spin) {
    float a = atan(p.y, p.x) + spin;
    float r = length(p);
    float shape = size * pow(abs(cos(a * count * 0.5)), 0.6);   // petal outline
    return exp(-abs(r - shape) * 140.0) * smoothstep(0.0, 0.02, r);
}

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.12, 0.02, 0.18), vec3(0.01, 0.0, 0.04), pow(y, 0.5));
    col += stars(d, 220.0, 0.94) * 0.7;

    // Looking up: zenith-centered coordinates.
    vec2 p = d.xz / (1.0 + max(d.y, 0.0)) * 1.6;
    float bloom = 1.0 + uBass * 0.12;
    float t = uTime * 0.05;

    vec3 glow = vec3(0.0);
    glow += vec3(1.0, 0.2, 0.75) * petalLine(p, 8.0, 0.95 * bloom, t);
    glow += vec3(0.2, 0.8, 1.0) * petalLine(p, 8.0, 0.7 * bloom, -t + 0.39);
    glow += vec3(0.7, 0.3, 1.0) * petalLine(p, 6.0, 0.45 * bloom, t * 1.5);
    glow += vec3(1.0, 0.85, 0.4) * petalLine(p, 12.0, 0.22 * bloom, -t * 2.0);
    float core = exp(-length(p) * 9.0);
    glow += vec3(1.0, 0.6, 0.9) * core * (0.8 + uBeat);

    // Soft haze and a reflection ring near the horizon.
    float halo = exp(-abs(length(p) - 1.25) * 6.0) * 0.15;
    glow += vec3(0.6, 0.2, 0.9) * halo * (1.0 + uLevel);
    col += glow * (0.9 + uMid * 0.5) * smoothstep(-0.1, 0.15, d.y);

    // Faint neon water line at the horizon.
    col += vec3(1.0, 0.3, 0.8) * exp(-abs(d.y) * 60.0) * 0.4;
    return col;
}
