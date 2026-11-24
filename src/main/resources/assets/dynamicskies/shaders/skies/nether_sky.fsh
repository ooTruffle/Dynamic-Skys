// Nether Sky: a low churning ceiling of red smoke, a lava sea glowing from below,
// ash drifting down and embers rising. The bass makes the lava pulse.

float specks(float az, float el, float n, float fall, float drift, float seed, float thresh) {
    float u = (az / TAU + 0.5) * n + uTime * drift;
    float v = el / TAU * n + uTime * fall;
    vec2 id = floor(vec2(u, v));
    vec2 f = fract(vec2(u, v)) - 0.5;
    vec2 key = vec2(mod(id.x, n), id.y) + seed;
    float h = hash12(key);
    if (h < thresh) return 0.0;
    vec2 o = (hash22(key) - 0.5) * 0.6;
    o.x += sin(uTime * 0.8 + h * 50.0) * 0.12;
    vec2 q = vec2((f.x - o.x) * cos(el), f.y - o.y);
    return exp(-dot(q, q) * 900.0) * (0.5 + 0.5 * h);
}

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x);
    float el = elevation(d);
    float pulse = 1.0 + uBass * 0.45 + uBeat * 0.3;

    vec3 col;
    if (el >= 0.0) {
        // Smoke ceiling: dark red overhead, brighter and hotter toward the horizon.
        vec3 sp = vec3(d.xz / (d.y + 0.25) * 0.8, uTime * 0.04);
        float n1 = fbm(sp + vec3(uTime * 0.02, 0.0, 0.0));
        float n2 = fbm(sp * 2.3 - vec3(0.0, uTime * 0.03, 0.0) + n1 * 1.5);
        float smoke = n1 * 0.6 + n2 * 0.6;
        float heat = exp(-el * 3.5);
        col = mix(vec3(0.08, 0.012, 0.01), vec3(0.22, 0.04, 0.02), smoothstep(0.3, 0.9, smoke));
        col = mix(col, vec3(0.03, 0.005, 0.005), smoothstep(0.55, 0.95, n2) * 0.7);
        col += vec3(0.9, 0.3, 0.05) * heat * heat * 0.6 * pulse;
        col += vec3(0.5, 0.12, 0.02) * heat * smoothstep(0.4, 0.8, smoke) * 0.4 * pulse;

        // Distant netherrack cliffs along the horizon, outlined by the lava light.
        float hm = 0.02 + 0.09 * fbm(ring(d, 3.0) + 21.0) + 0.03 * noise(ring(d, 14.0));
        if (el < hm) {
            col = vec3(0.07, 0.015, 0.012) + vec3(0.6, 0.15, 0.03) * exp(-el * 25.0) * pulse;
            col += vec3(1.0, 0.45, 0.1) * exp(-(hm - el) * 220.0) * 0.25;
        }
    } else {
        // Lava sea: bright cracks under a dark crust, shifting slowly.
        vec2 lp = d.xz / (-d.y + 0.03);
        float n = fbm(lp * 0.5 + vec2(uTime * 0.05, uTime * 0.02));
        float n2 = fbm(lp * 1.3 - vec2(uTime * 0.04, 0.0) + n * 2.0);
        float vein = 1.0 - smoothstep(0.0, 0.05, abs(n2 - 0.5));
        float pool = smoothstep(0.34, 0.24, n) * 0.85;
        float hot = clamp(vein * 0.8 + pool, 0.0, 1.0);
        vec3 lava = mix(vec3(0.85, 0.2, 0.02), vec3(1.0, 0.7, 0.2), hot * hot);
        vec3 crust = mix(vec3(0.1, 0.02, 0.012), vec3(0.22, 0.05, 0.02), n2);
        col = mix(crust, lava, hot) * pulse;
        // Haze over the far lava blends it into the glowing horizon.
        col = mix(col, vec3(0.9, 0.3, 0.06) * pulse, exp(el * 15.0) * 0.6);
    }

    // Ash falling, and a few bright embers floating up.
    float ash = specks(az, el, 220.0, 0.06, 0.15, 0.0, 0.8) + specks(az, el, 140.0, 0.04, 0.1, 5.0, 0.85) * 0.8;
    col += vec3(0.55, 0.5, 0.48) * ash * 0.5 * smoothstep(-0.05, 0.1, el);
    float embers = specks(az, el, 160.0, -0.08, 0.05, 11.0, 0.93);
    col += vec3(1.0, 0.55, 0.15) * embers * (1.5 + uHigh) * exp(-max(el, 0.0) * 1.5);
    return col;
}
