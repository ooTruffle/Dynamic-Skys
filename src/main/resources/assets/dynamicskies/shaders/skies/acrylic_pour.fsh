// Acrylic Pour: slow fluid-art swirls of paint, with white lacing between the colors.

vec3 sky(vec3 d) {
    float t = uTime * 0.03;
    vec3 p = d * 1.6;
    vec3 q = vec3(fbm3(p + vec3(t, 0.0, 0.0)), fbm3(p + vec3(3.1, t, 1.7)), fbm3(p + vec3(7.2, 2.4, -t)));
    vec3 r = vec3(fbm3(p + q * 2.5 + vec3(1.2, 4.3, t)), fbm3(p + q * 2.5 + vec3(5.1, 1.3, -t)), 0.0);
    float n = fbm(p + r.xyx * 2.2 + vec3(uMid * 0.2));

    // Paint layers: hard bands of color.
    float bands = n * 7.0;
    float band = floor(bands);
    float within = fract(bands);
    vec3 c0 = vec3(0.04, 0.32, 0.4);   // deep teal
    vec3 c1 = vec3(0.93, 0.72, 0.3);   // gold
    vec3 c2 = vec3(0.75, 0.2, 0.45);   // magenta
    vec3 c3 = vec3(0.1, 0.12, 0.25);   // navy
    vec3 c4 = vec3(0.95, 0.93, 0.88);  // white
    float k = mod(band, 5.0);
    vec3 col = k < 1.0 ? c0 : k < 2.0 ? c1 : k < 3.0 ? c2 : k < 4.0 ? c3 : c4;

    // Cells and lacing where layers meet.
    float lace = smoothstep(0.08, 0.0, min(within, 1.0 - within));
    float cells = smoothstep(0.55, 0.6, fbm(p * 14.0 + r * 3.0)) * smoothstep(0.3, 0.0, abs(within - 0.5) - 0.2);
    col = mix(col, vec3(1.0), lace * 0.85);
    col = mix(col, col * 0.4, cells * 0.6);

    // Glossy sheen.
    col += vec3(1.0) * pow(max(0.0, q.x - 0.55), 3.0) * 4.0;
    col *= 0.8 + uBeat * 0.25;
    return col;
}
