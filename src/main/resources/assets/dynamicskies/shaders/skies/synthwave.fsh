// Synthwave: striped retro sun, mountains and a scrolling neon grid.

vec3 sky(vec3 d) {
    vec3 col;
    if (d.y >= 0.0) {
        float t = pow(d.y, 0.6);
        col = mix(vec3(1.0, 0.25, 0.55), vec3(0.1, 0.02, 0.22), smoothstep(0.0, 0.6, t));
        col = mix(col, vec3(0.015, 0.0, 0.05), smoothstep(0.45, 1.0, d.y));
        col += stars(d, 180.0, 0.93) * smoothstep(0.15, 0.5, d.y);

        vec3 sd = normalize(vec3(0.0, 0.18, -1.0));
        float r = acos(clamp(dot(d, sd), -1.0, 1.0));
        float rad = 0.3 * (1.0 + uBass * 0.12);
        float rel = (d.y - sd.y) / rad;
        vec3 sunCol = mix(vec3(1.0, 0.15, 0.6), vec3(1.0, 0.85, 0.2), clamp(rel * 0.5 + 0.5, 0.0, 1.0));
        float stripes = step(0.0, sin((rel * 9.0 + uTime * 0.5) * PI));
        float cut = rel < 0.0 ? mix(1.0, stripes, smoothstep(0.05, -0.25, rel)) : 1.0;
        float disk = smoothstep(rad, rad - 0.006, r) * cut;
        col += sunCol * 0.35 * exp(-r * 4.0) * (1.0 + uLevel);
        col = mix(col, sunCol * (1.2 + uBeat * 0.5), disk);

        float hm = 0.015 + 0.11 * fbm(ring(d, 2.5) + 10.0);
        if (d.y < hm) {
            float edge = smoothstep(hm - 0.006, hm, d.y);
            col = mix(vec3(0.05, 0.0, 0.1), vec3(0.9, 0.1, 0.9), edge);
        }
    } else {
        vec2 p = d.xz / (-d.y);
        float far = length(p);
        p.y += uTime * 2.0;
        vec2 gp = p * 0.5;
        vec2 g = abs(fract(gp - 0.5) - 0.5) / max(fwidth(gp), vec2(1e-4));
        float line = 1.0 - min(min(g.x, g.y), 1.0);
        float fade = exp(-far * 0.04);
        col = vec3(0.03, 0.0, 0.06);
        col += vec3(1.0, 0.1, 0.85) * line * fade * (1.0 + uBass * 1.5 + uBeat);
        col += vec3(1.0, 0.2, 0.6) * 0.35 * exp(d.y * 25.0);
    }
    return col;
}
