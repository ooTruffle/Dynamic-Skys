// Golden Hour: warm low sun and soft drifting clouds.

vec3 sky(vec3 d) {
    vec3 sd = normalize(vec3(1.0, 0.07, 0.3));
    float s = max(dot(d, sd), 0.0);
    float y = max(d.y, 0.0);

    vec3 col = mix(vec3(1.0, 0.62, 0.32), vec3(0.32, 0.45, 0.75), smoothstep(0.0, 0.55, y));
    col = mix(col, vec3(0.15, 0.25, 0.55), smoothstep(0.5, 1.0, y));
    col += vec3(1.0, 0.55, 0.2) * pow(s, 6.0) * 0.6;
    col += vec3(1.0, 0.8, 0.5) * pow(s, 60.0) * (1.0 + uBass * 0.8);
    col += vec3(1.0, 0.95, 0.8) * smoothstep(0.9993, 0.9997, s) * 3.0;

    if (d.y > 0.0) {
        vec2 p = d.xz / (d.y + 0.08) * 0.7 + vec2(uTime * 0.02, uTime * 0.005);
        float c = fbm(p);
        float cloud = smoothstep(0.48, 0.78, c) * smoothstep(0.0, 0.12, d.y);
        vec3 lit = mix(vec3(0.95, 0.5, 0.45), vec3(1.0, 0.8, 0.55), pow(s, 3.0));
        col = mix(col, lit * (0.85 + uMid * 0.3), cloud * 0.85);
    } else {
        col = mix(col, vec3(0.35, 0.2, 0.18), smoothstep(0.0, -0.3, d.y));
    }
    return col;
}
