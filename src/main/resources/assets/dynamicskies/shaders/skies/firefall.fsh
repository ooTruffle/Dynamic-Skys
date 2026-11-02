// Firefall: streaks of fire rain down through a smoldering sky while embers drift up.

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.55, 0.12, 0.02), vec3(0.06, 0.01, 0.01), pow(y, 0.5));
    float smoke = fbm(vec2(atan(d.z, d.x) * 2.0, d.y * 3.0 - uTime * 0.05) + fbm(d.xz * 2.0));
    col = mix(col, vec3(0.08, 0.02, 0.02), smoothstep(0.4, 0.75, smoke) * 0.7);

    float az = atan(d.z, d.x) / TAU + 0.5;
    float el = elevation(d) / PI + 0.5;

    // Falling fire streaks.
    vec3 fire = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float fl = float(layer);
        float cols = 70.0 + fl * 40.0;
        float x = az * cols;
        float cid = floor(x);
        float h = hash11(cid * 1.7 + fl * 13.0);
        if (h < 0.55) continue;
        float speed = (0.25 + h * 0.35) * (1.0 + uBass * 0.5);
        float headY = 1.0 - fract(uTime * speed * 0.3 + h * 7.0);
        float cx = abs(fract(x) - 0.5);
        float trail = (el - headY);
        float len = 0.12 + h * 0.1;
        float streak = smoothstep(0.18, 0.0, cx) * step(0.0, trail) * smoothstep(len, 0.0, trail);
        float headGlow = exp(-length(vec2(cx * 0.02, trail)) * 120.0);
        vec3 c = mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.85, 0.4), streak * streak);
        fire += c * (streak * 0.8 + headGlow * 1.5) * (1.0 - fl * 0.25);
    }
    col += fire * (1.0 + uBeat * 0.8);

    // Rising embers.
    vec2 g = vec2(az * 160.0, el * 80.0 - uTime * 1.5);
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float eh = hash12(id);
    if (eh > 0.9) {
        vec2 o = (hash22(id) - 0.5) * 0.6;
        float e = smoothstep(0.12, 0.0, length(f - o));
        col += vec3(1.0, 0.5, 0.1) * e * (0.5 + 0.5 * sin(uTime * 6.0 + eh * 40.0)) * (1.0 + uHigh);
    }

    col += vec3(1.0, 0.35, 0.05) * exp(-abs(d.y) * 10.0) * 0.35 * (1.0 + uLevel);
    return col;
}
