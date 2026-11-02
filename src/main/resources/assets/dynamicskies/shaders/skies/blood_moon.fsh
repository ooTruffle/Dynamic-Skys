// Blood Moon: a huge cratered red moon over a crimson night.

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.25, 0.02, 0.02), vec3(0.03, 0.0, 0.01), pow(y, 0.5));
    col += stars(d, 200.0, 0.94) * vec3(1.0, 0.7, 0.6) * smoothstep(0.05, 0.4, d.y);

    vec3 md = normalize(vec3(-0.4, 0.45, -0.8));
    float c = dot(d, md);
    float r = acos(clamp(c, -1.0, 1.0));
    float rad = 0.14 * (1.0 + uBass * 0.06);

    col += vec3(0.8, 0.08, 0.03) * exp(-r * 6.0) * (0.6 + uLevel * 0.8 + uBeat * 0.5);
    if (r < rad) {
        float k = r / rad;
        float craters = fbm(d * 40.0) * 0.6 + fbm3(d * 120.0) * 0.4;
        float limb = sqrt(1.0 - k * k);
        vec3 moon = mix(vec3(0.45, 0.04, 0.02), vec3(1.0, 0.25, 0.1), craters) * (0.35 + 0.75 * limb);
        col = mix(col, moon * 1.2, smoothstep(rad, rad - 0.004, r));
    }

    if (d.y > 0.0) {
        vec2 p = d.xz / (d.y + 0.1) * 0.5 + vec2(uTime * 0.015, 0.0);
        float cl = smoothstep(0.5, 0.8, fbm(p)) * smoothstep(0.0, 0.1, d.y) * smoothstep(0.7, 0.2, d.y);
        col = mix(col, vec3(0.08, 0.0, 0.0), cl * 0.8);
    }
    return col;
}
