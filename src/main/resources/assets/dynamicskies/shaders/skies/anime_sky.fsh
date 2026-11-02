// Anime Sky: a vivid summer sky with big crisp cumulus clouds, a bright sun and sparkles.

vec3 sky(vec3 d) {
    vec3 sd = normalize(vec3(0.5, 0.45, -0.7));
    float s = max(dot(d, sd), 0.0);
    float y = max(d.y, 0.0);

    vec3 col = mix(vec3(0.62, 0.85, 1.0), vec3(0.1, 0.42, 0.95), smoothstep(0.0, 0.7, y));
    col = mix(col, vec3(0.05, 0.25, 0.75), smoothstep(0.6, 1.0, y));
    col += vec3(1.0, 0.95, 0.8) * pow(s, 8.0) * 0.5;
    col += vec3(1.0) * smoothstep(0.9985, 0.9992, s) * 2.0;
    col += vec3(1.0, 0.98, 0.9) * pow(s, 400.0) * 3.0;

    if (d.y > 0.0) {
        // Cumulus: thresholded noise with a hard edge, bright tops and blue-grey bellies.
        vec2 p = d.xz / (d.y + 0.25) * 0.55 + vec2(uTime * 0.012, 0.0);
        float base = fbm(p * 1.2) + 0.35 * fbm(p * 3.5);
        float height = smoothstep(0.0, 0.5, d.y);
        float cloud = smoothstep(0.78, 0.8, base + height * 0.12);
        float top = smoothstep(0.78, 1.05, base + (fbm(p * 3.5 + vec2(0.05, 0.05)) - 0.5) * 0.3);
        vec3 belly = vec3(0.55, 0.62, 0.8);
        vec3 lit = mix(vec3(1.0), vec3(1.0, 0.97, 0.92), s);
        vec3 cc = mix(belly, lit, top);
        cc += vec3(1.0, 0.95, 0.85) * smoothstep(0.8, 0.84, base + height * 0.12) * (1.0 - top) * 0.25 * pow(s, 2.0);
        col = mix(col, cc, cloud * smoothstep(0.0, 0.06, d.y));

        // A thin contrail.
        vec3 cn = normalize(vec3(0.15, 0.25, 1.0));
        float trail = exp(-abs(dot(d, cn)) * 300.0) * smoothstep(0.1, 0.3, d.y) * (0.6 + 0.4 * noise(d.xz * 30.0));
        col = mix(col, vec3(1.0), trail * 0.6);
    } else {
        col = mix(col, vec3(0.75, 0.88, 1.0), smoothstep(0.0, -0.2, d.y));
    }

    // Sparkles near the sun.
    float sp = stars(d, 120.0, 0.97) * smoothstep(0.85, 0.98, s);
    col += vec3(1.0) * sp * (1.0 + uBeat);
    return col * (0.95 + uLevel * 0.15);
}
