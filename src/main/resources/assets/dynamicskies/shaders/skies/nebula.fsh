// Nebula: warped glowing gas clouds and stars.

vec3 sky(vec3 d) {
    float t = uTime * 0.03;
    vec3 p = d * 2.2 + vec3(0.0, 0.0, t);
    vec3 q = vec3(fbm3(p), fbm3(p + vec3(5.2, 1.3, 2.8)), fbm3(p + vec3(9.1, 4.7, 1.1)));
    float n = fbm(p + q * (2.5 + uMid * 0.8) + vec3(t));

    vec3 col = vec3(0.01, 0.0, 0.025);
    col = mix(col, vec3(0.45, 0.08, 0.55), smoothstep(0.35, 0.7, n));
    col += vec3(0.05, 0.35, 0.8) * smoothstep(0.5, 0.85, q.x) * 0.7;
    col += vec3(1.0, 0.55, 0.75) * pow(clamp(n, 0.0, 1.0), 4.0) * 2.0;
    col *= 0.8 + uLevel * 0.7 + uBeat * 0.4;

    float dust = smoothstep(0.3, 0.8, n);
    col += stars(d, 240.0, 0.9) * (1.0 - dust * 0.7);
    col += stars(d, 80.0, 0.975) * vec3(0.9, 0.8, 1.0);
    return col;
}
