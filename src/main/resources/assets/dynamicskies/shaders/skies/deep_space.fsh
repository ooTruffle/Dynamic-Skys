// Deep Space: dense starfield and a dusty galactic band arching overhead.

vec3 sky(vec3 d) {
    vec3 n = normalize(vec3(1.0, 0.25, 0.35));
    float lat = dot(d, n);
    float band = exp(-lat * lat * 14.0);
    float core = exp(-lat * lat * 60.0);

    float dust = fbm(d * 4.0 + vec3(uTime * 0.005));
    float lanes = smoothstep(0.45, 0.65, fbm3(d * 9.0));
    vec3 glow = mix(vec3(0.3, 0.25, 0.5), vec3(1.0, 0.85, 0.7), dust) * band * (0.35 + dust * 0.6);
    glow += vec3(1.0, 0.9, 0.75) * core * dust * 0.5;
    glow *= 1.0 - lanes * 0.75 * core;
    glow *= 0.8 + uLevel * 0.6;

    vec3 col = vec3(0.004, 0.005, 0.014) + glow;
    float dense = 0.86 - band * 0.12;
    col += stars(d, 300.0, dense) * vec3(0.9, 0.93, 1.0);
    col += stars(d, 140.0, 0.93) * vec3(1.0, 0.92, 0.8);
    col += stars(d, 50.0, 0.98) * vec3(0.8, 0.9, 1.0) * 1.5;
    return col;
}
