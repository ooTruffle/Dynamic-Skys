// Kaleidoscope: mirrored neon fractal rings centered overhead.

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x);
    float r = acos(clamp(d.y, -1.0, 1.0));
    float seg = TAU / 8.0;
    float a = mod(az + uTime * 0.05, seg);
    a = abs(a - seg * 0.5);
    vec2 uv = vec2(cos(a), sin(a)) * r * 1.1;
    vec2 uv0 = uv;
    vec3 col = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        uv = fract(uv * 1.5) - 0.5;
        float dd = length(uv) * exp(-length(uv0));
        vec3 c = pal(length(uv0) + float(i) * 0.4 + uTime * 0.2,
                     vec3(0.5), vec3(0.5), vec3(1.0), vec3(0.263, 0.416, 0.557));
        dd = sin(dd * 8.0 + uTime * 0.8 + uBass * 1.5) / 8.0;
        dd = abs(dd);
        dd = pow(0.01 / max(dd, 1e-4), 1.2);
        col += c * min(dd, 6.0);
    }
    return col * (0.45 + uLevel * 0.3);
}
