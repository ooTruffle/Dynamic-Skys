// Swirling Night: painterly swirling strokes and big glowing stars.

vec3 sky(vec3 d) {
    vec2 uv = d.xz / (1.0 + max(d.y, -0.9)) * 2.0;
    float t = uTime * 0.15;
    for (int i = 0; i < 3; i++) {
        uv += 0.18 * vec2(sin(uv.y * 2.5 + t + float(i)), cos(uv.x * 2.5 - t * 0.8 + float(i) * 1.7));
    }
    float n = fbm(uv * 1.5);
    float strokes = 0.5 + 0.5 * sin((uv.x * 0.7 + uv.y) * 30.0 + n * 12.0);

    vec3 col = mix(vec3(0.03, 0.08, 0.25), vec3(0.2, 0.4, 0.75), n);
    col = mix(col, vec3(0.45, 0.65, 0.9), strokes * smoothstep(0.45, 0.8, n) * 0.5);
    col *= 0.8 + uMid * 0.3;

    // Big haloed stars.
    vec2 sg = d.xz / (1.0 + max(d.y, -0.9)) * 3.0;
    vec2 id = floor(sg);
    vec2 f = fract(sg) - 0.5;
    float h = hash12(id);
    if (h > 0.75 && d.y > 0.05) {
        vec2 o = (hash22(id) - 0.5) * 0.5;
        float r = length(f - o);
        float glow = 0.02 / (r + 0.01) + 0.25 * pow(max(0.0, sin(r * 50.0 - uTime * 2.0)), 4.0) * smoothstep(0.35, 0.1, r);
        col += vec3(1.0, 0.9, 0.45) * glow * (0.6 + 0.4 * sin(uTime + h * 40.0)) * (1.0 + uBeat * 0.6);
    }

    // Crescent moon.
    vec3 md = normalize(vec3(0.7, 0.55, -0.4));
    float m = acos(clamp(dot(d, md), -1.0, 1.0));
    vec3 md2 = normalize(md + vec3(0.03, 0.02, 0.0));
    float m2 = acos(clamp(dot(d, md2), -1.0, 1.0));
    float crescent = smoothstep(0.07, 0.065, m) * smoothstep(0.055, 0.065, m2);
    col += vec3(1.0, 0.85, 0.35) * (crescent * 1.5 + exp(-m * 12.0) * 0.4);
    return col;
}
