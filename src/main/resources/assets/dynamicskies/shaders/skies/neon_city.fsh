// Neon City: a rainy night over a city skyline, low clouds lit pink and cyan from below,
// rain streaking down and the lights reflected in wet streets. The bass pulses the glow.

// Skyline at this azimuth (0..1 around). Returns x = building top (radians),
// y = window light, z = 1 if inside a building.
vec3 skyline(float u, float el, float slots, float minH, float maxH, float seed) {
    float s = u * slots;
    float id = floor(s);
    float fx = fract(s);
    float h1 = hash12(vec2(mod(id, slots), seed));
    float h2 = hash12(vec2(mod(id, slots), seed + 5.0));
    float top = minH + (maxH - minH) * h1 * h1;
    // Some buildings are narrower, leaving a gap to the next.
    float inset = step(0.6, h2) * 0.18;
    float inside = step(el, top) * step(inset, fx) * step(fx, 1.0 - inset);

    // Window grid.
    vec2 wc = vec2(fx * 6.0, el * 180.0);
    vec2 wi = floor(wc);
    vec2 wf = fract(wc);
    float lit = step(0.62, hash12(wi + vec2(mod(id, slots) * 7.0, seed * 3.0)));
    float win = step(0.25, wf.x) * step(wf.x, 0.75) * step(0.3, wf.y) * step(wf.y, 0.75);
    float flick = 0.8 + 0.2 * sin(uTime * (1.0 + h2 * 3.0) + id);
    float windows = lit * win * flick * step(el, top - 0.006);

    // A red light blinking on the tallest roofs.
    float beacon = step(0.8, h1) * exp(-length(vec2((fx - 0.5) * TAU / slots, el - top - 0.003)) * 900.0)
                 * step(0.5, fract(uTime * 0.5 + h2));
    return vec3(top, windows + beacon * 4.0, inside);
}

vec3 cityColor(float u, float el) {
    vec3 col = vec3(0.0);
    vec3 far = skyline(u, el, 160.0, 0.03, 0.16, 1.0);
    vec3 near = skyline(u + 0.003, el, 70.0, 0.02, 0.26, 2.0);
    float hue = hash11(mod(floor(u * 160.0), 160.0));
    vec3 winCol = mix(vec3(1.0, 0.75, 0.45), vec3(0.4, 0.9, 1.0), step(0.6, hue));
    winCol = mix(winCol, vec3(1.0, 0.3, 0.8), step(0.85, hue));
    if (near.z > 0.5) {
        col = vec3(0.012, 0.01, 0.02) + winCol * near.y * 0.6;
    } else if (far.z > 0.5) {
        col = vec3(0.03, 0.02, 0.05) + winCol * far.y * 0.35;
    } else {
        col = vec3(-1.0);
    }
    return col;
}

float rain(vec3 d, float el, float az, float lanes, float speed, float seed) {
    float lp = (az / TAU + 0.5) * lanes + el * 2.0; // slanted a little
    float lane = floor(lp);
    float fx = fract(lp) - 0.5;
    float h = hash12(vec2(mod(lane, lanes), seed));
    float v = el * 3.0 + uTime * speed * (0.8 + h * 0.4) + h * 10.0;
    float f = fract(v);
    float on = step(0.55, hash12(vec2(mod(lane, lanes), floor(v) + seed)));
    float streak = smoothstep(0.0, 0.18, f) * step(f, 0.18);
    return streak * on * exp(-fx * fx * 60.0);
}

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x);
    float el = elevation(d);
    float u = az / TAU + 0.5;
    float pulse = 1.0 + uBass * 0.5 + uBeat * 0.3;

    // Low clouds, glowing magenta and cyan where the city lights hit them.
    float n = fbm(vec3(ring(d, 2.0), el * 3.0 + uTime * 0.03));
    float tone = fbm(ring(d, 1.2) + 4.0);
    vec3 neon = mix(vec3(1.0, 0.2, 0.6), vec3(0.2, 0.8, 1.0), smoothstep(0.35, 0.65, tone));
    vec3 col = vec3(0.02, 0.015, 0.04);
    col += neon * 0.35 * exp(-max(el, 0.0) * 5.0) * pulse;
    col += neon * 0.25 * smoothstep(0.35, 0.8, n) * exp(-max(el, 0.0) * 2.5) * pulse;
    col = mix(col, vec3(0.01, 0.008, 0.02), smoothstep(0.3, 1.2, el));

    if (el >= 0.0) {
        vec3 city = cityColor(u, el);
        if (city.x >= 0.0) col = city + neon * 0.04;
    } else {
        // Wet streets: the skyline mirrored and smeared by ripples.
        float rip = (noise(vec2(az * 60.0, el * 30.0 - uTime * 2.0)) - 0.5) * 0.004;
        vec3 refl = cityColor(u + rip, -el * 1.4 + rip * 4.0);
        col = vec3(0.01, 0.008, 0.02) + neon * 0.12 * exp(el * 8.0) * pulse;
        if (refl.x >= 0.0) col += refl * 0.4;
        col += neon * 0.08 * smoothstep(0.6, 0.9, noise(vec2(az * 30.0, el * 80.0))) * exp(el * 4.0);
    }

    // Rain in front of everything, lit by the city glow.
    float r = rain(d, el, az, 700.0, 2.2, 1.0) + rain(d, el, az, 420.0, 1.6, 2.0) * 0.6;
    r *= 1.0 - smoothstep(0.9, 1.4, el);
    col += mix(vec3(0.35, 0.4, 0.5), neon, 0.4) * r * (0.12 + 0.3 * exp(-abs(el) * 4.0));
    return col;
}
