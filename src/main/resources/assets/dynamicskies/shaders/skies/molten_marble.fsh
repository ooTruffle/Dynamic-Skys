// Molten Marble: slow liquid stone with glowing gold veins.

vec3 sky(vec3 d) {
    float t = uTime * 0.04;
    vec3 p = d * 1.8;
    vec3 q = vec3(fbm3(p + t), fbm3(p + vec3(4.1, 1.7, 8.3) - t), fbm3(p + vec3(2.9, 7.2, 3.3)));
    vec3 r = vec3(fbm3(p + q * 3.0 + vec3(1.7, 9.2, t)), fbm3(p + q * 3.0 + vec3(8.3, 2.8, -t)), 0.0);
    float n = fbm(p + r.xyx * 3.5);

    vec3 col = mix(vec3(0.03, 0.0, 0.0), vec3(0.45, 0.04, 0.02), smoothstep(0.2, 0.6, n));
    col = mix(col, vec3(1.0, 0.45, 0.08), smoothstep(0.55, 0.8, n));
    float vein = pow(abs(sin(n * 22.0 + q.x * 6.0)), 18.0);
    col += vec3(1.0, 0.8, 0.35) * vein * (0.6 + uMid * 0.8 + uBeat * 0.5);
    return col * (0.85 + uBass * 0.3);
}
