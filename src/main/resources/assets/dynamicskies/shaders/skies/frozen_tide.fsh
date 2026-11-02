// Frozen Tide: icy crystal cells with cold light washing across them.

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.35, 0.75, 0.9), vec3(0.02, 0.06, 0.18), pow(y, 0.5));

    vec2 v = voronoi(d * 6.0);
    float edge = smoothstep(0.08, 0.0, v.y - v.x);
    float facet = hash13(floor(d * 6.0 + 0.5));
    col += vec3(0.6, 0.9, 1.0) * edge * 0.5 * (1.0 + uHigh);
    col += vec3(0.2, 0.5, 0.7) * facet * 0.08;

    float tide = sin(d.y * 14.0 - uTime * 0.6 + fbm(ring(d, 3.0)) * 6.0);
    col += vec3(0.4, 0.9, 1.0) * pow(max(tide, 0.0), 6.0) * 0.35 * (1.0 + uBass * 1.5);
    col += stars(d, 200.0, 0.95) * smoothstep(0.3, 0.7, d.y);
    return col;
}
