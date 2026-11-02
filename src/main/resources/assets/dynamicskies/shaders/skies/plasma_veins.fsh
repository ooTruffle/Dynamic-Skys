// Plasma Veins: branching electric veins pulsing through violet haze.

vec3 sky(vec3 d) {
    vec3 p = d * 2.5;
    float t = uTime * 0.15;
    vec3 w = vec3(fbm3(p + vec3(t, 0.0, 0.0)), fbm3(p + vec3(0.0, t, 3.1)), fbm3(p + vec3(7.3, 2.1, -t)));
    float n = fbm(p * 1.5 + w * 2.0);
    float v = 0.012 * (1.0 + uMid * 1.5) / (abs(n - 0.5) + 0.003);
    float n2 = fbm3(p * 3.0 - w + vec3(t * 2.0));
    float v2 = 0.006 * (1.0 + uHigh) / (abs(n2 - 0.5) + 0.003);

    vec3 col = vec3(0.012, 0.0, 0.03) + vec3(0.25, 0.05, 0.4) * w.x * 0.4;
    col += vec3(0.45, 0.3, 1.0) * min(v, 4.0) * (1.0 + uBeat * 0.8 + uBass * 0.5);
    col += vec3(1.0, 0.3, 0.8) * min(v2, 3.0) * 0.4;
    return col;
}
