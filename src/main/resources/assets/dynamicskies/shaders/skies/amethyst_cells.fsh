// Amethyst Cells: a dome of violet crystal cells, each facet catching light and pulsing.

vec3 sky(vec3 d) {
    vec3 p = d * 5.0 + vec3(0.0, uTime * 0.02, 0.0);
    vec2 v = voronoi(p);
    vec3 id = floor(p + 0.5);

    float edge = smoothstep(0.07, 0.0, v.y - v.x);
    float facet = v.x;                                   // distance to cell center
    float h = hash13(floor(p) + hash33(floor(p)).x);
    float pulse = 0.5 + 0.5 * sin(uTime * (0.8 + h * 1.5) + h * 30.0);

    vec3 deep = vec3(0.12, 0.02, 0.22);
    vec3 bright = mix(vec3(0.55, 0.2, 0.85), vec3(0.85, 0.55, 1.0), h);
    vec3 col = mix(bright, deep, smoothstep(0.0, 0.8, facet));
    col *= 0.55 + 0.45 * pulse * (1.0 + uMid * 0.6);

    // Sparkling crystal edges.
    col += vec3(0.95, 0.8, 1.0) * edge * (0.6 + uHigh * 0.8);
    float sparkle = pow(max(0.0, sin(uTime * 2.0 + h * 50.0)), 30.0) * smoothstep(0.25, 0.0, facet);
    col += vec3(1.0) * sparkle * (0.8 + uBeat);

    col *= 0.5 + 0.5 * smoothstep(-0.4, 0.5, d.y);
    return col;
}
