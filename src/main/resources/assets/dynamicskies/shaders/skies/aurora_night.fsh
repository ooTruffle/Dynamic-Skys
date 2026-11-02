// Aurora Night: layered green/violet curtains over a starry night.

float auroraLayer(vec2 p, float t) {
    float n = fbm3(vec3(p.x * 0.6 + t * 0.05, p.y * 0.25 - t * 0.02, t * 0.03));
    float band = abs(sin(p.x * 0.7 + n * 6.0 + t * 0.12));
    float ridge = pow(1.0 - band, 5.0);
    return ridge * smoothstep(0.25, 0.75, n);
}

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.015, 0.03, 0.07), vec3(0.0, 0.004, 0.02), sqrt(y));
    col += stars(d, 230.0, 0.9) * vec3(0.85, 0.92, 1.0);
    col += stars(d, 90.0, 0.97) * vec3(1.0, 0.95, 0.85);

    if (d.y > 0.0) {
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 16; i++) {
            float fi = float(i) / 16.0;
            float h = 0.8 + fi * 0.7;
            vec2 p = d.xz / (d.y + 0.2) * h * 1.6;
            float a = auroraLayer(p, uTime);
            vec3 c = mix(vec3(0.1, 1.0, 0.45), vec3(0.6, 0.15, 1.0), fi);
            acc += c * a * (1.0 - fi) * 0.16;
        }
        acc *= 1.0 + uBass * 1.6 + uBeat * 0.8;
        col += acc * smoothstep(0.0, 0.18, d.y);
    }
    return col;
}
