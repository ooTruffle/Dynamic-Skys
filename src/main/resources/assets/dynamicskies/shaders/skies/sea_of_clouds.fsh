// Sea of Clouds: floating above an endless cloud ocean at dusk.

vec3 sky(vec3 d) {
    vec3 sd = normalize(vec3(-1.0, 0.06, 0.2));
    float s = max(dot(d, sd), 0.0);
    float y = max(d.y, 0.0);

    vec3 col = mix(vec3(1.0, 0.6, 0.5), vec3(0.25, 0.3, 0.6), smoothstep(0.0, 0.4, y));
    col = mix(col, vec3(0.06, 0.08, 0.22), smoothstep(0.35, 1.0, y));
    col += vec3(1.0, 0.7, 0.4) * pow(s, 12.0) * 0.6;
    col += vec3(1.0, 0.9, 0.7) * pow(s, 300.0) * 2.0;
    col += stars(d, 200.0, 0.95) * smoothstep(0.4, 0.8, d.y);

    if (d.y < 0.03) {
        float depth = -d.y + 0.035;
        vec2 p = d.xz / depth * 0.25 + vec2(uTime * 0.03, 0.0);
        float c = fbm(p + vec2(0.0, uBass * 0.15));
        float c2 = fbm(p * 2.3 - vec2(uTime * 0.02, 0.0));
        float h = c * 0.7 + c2 * 0.3;
        vec3 top = mix(vec3(1.0, 0.75, 0.65), vec3(1.0, 0.9, 0.85), pow(s, 4.0));
        vec3 low = vec3(0.3, 0.3, 0.5);
        vec3 sea = mix(low, top, smoothstep(0.3, 0.75, h)) * (0.85 + uLevel * 0.25);
        float haze = exp(-depth * 18.0);
        sea = mix(sea, col, haze * 0.8);
        col = mix(col, sea, smoothstep(0.03, 0.0, d.y));
    }
    return col;
}
