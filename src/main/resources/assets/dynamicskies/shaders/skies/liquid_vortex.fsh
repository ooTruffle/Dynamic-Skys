// Liquid Vortex: a twisting whirlpool of color spinning overhead.

vec3 sky(vec3 d) {
    float r = acos(clamp(d.y, -1.0, 1.0));
    float a = atan(d.z, d.x);
    float twist = a + r * (3.0 + uBass * 1.5) - uTime * 0.4;
    vec2 p = vec2(cos(twist), sin(twist)) * (r * 2.0 + 0.3);
    float n = fbm(p * 1.5 + vec2(uTime * 0.05));
    float bands = 0.5 + 0.5 * sin(r * 10.0 - uTime * 1.2 + n * 6.0);

    vec3 col = pal(n + r * 0.2 + uTime * 0.02, vec3(0.5, 0.4, 0.5), vec3(0.5, 0.4, 0.5),
                   vec3(1.0, 1.0, 1.0), vec3(0.0, 0.33, 0.67));
    col *= 0.35 + 0.65 * bands;
    col += vec3(1.0, 0.9, 1.0) * exp(-r * 6.0) * (0.6 + uLevel + uBeat * 0.6);
    return col * 0.8;
}
