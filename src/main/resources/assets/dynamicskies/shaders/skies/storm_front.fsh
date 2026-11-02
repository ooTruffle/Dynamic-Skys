// Storm Front: fast heavy clouds and lightning that also strikes on beats.

vec3 sky(vec3 d) {
    float slot = floor(uTime * 1.3);
    float ph = fract(uTime * 1.3);
    float flash = step(0.72, hash11(slot)) * exp(-ph * 9.0);
    flash = max(flash, uBeat * 0.9);

    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.42, 0.46, 0.52), vec3(0.2, 0.22, 0.27), sqrt(y));

    if (d.y > 0.0) {
        vec2 p = d.xz / (d.y + 0.12) * 0.6;
        vec2 drift = vec2(uTime * 0.08, uTime * 0.03);
        float c1 = fbm(p + drift);
        float c2 = fbm(p * 2.0 + drift * 1.7 + c1);
        float dens = c1 * 0.6 + c2 * 0.5;
        float cloud = smoothstep(0.3, 0.7, dens);
        float shade = smoothstep(0.45, 0.9, dens);
        vec3 cc = mix(vec3(0.38, 0.4, 0.46), vec3(0.1, 0.11, 0.14), shade);
        cc += vec3(0.15, 0.16, 0.2) * smoothstep(0.5, 0.3, abs(dens - 0.45));
        col = mix(col, cc, cloud * smoothstep(0.0, 0.08, d.y));
        col += vec3(0.6, 0.65, 0.85) * flash * (0.4 + shade * 0.6) * 0.6;
    }

    // Bolt
    float a0 = (hash11(slot * 3.7) - 0.5) * TAU;
    float az = atan(d.z, d.x);
    float el = elevation(d);
    float dx = mod(az - a0 + PI, TAU) - PI;
    dx += (noise(vec2(el * 18.0, slot)) - 0.5) * 0.12 + (noise(vec2(el * 60.0, slot)) - 0.5) * 0.03;
    float top = 0.25 + hash11(slot * 1.3) * 0.25;
    float bolt = 0.0015 / (abs(dx) + 0.001) * step(0.0, el) * step(el, top);
    col += vec3(0.75, 0.8, 1.0) * min(bolt, 3.0) * flash;
    return col;
}
