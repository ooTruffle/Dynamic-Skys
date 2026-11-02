// Silk: soft flowing folds of shimmering fabric.

vec3 sky(vec3 d) {
    float t = uTime * 0.12;
    vec3 p = d * 2.0;
    float w = fbm3(p + vec3(t * 0.5));
    float f = sin(p.x * 3.0 + p.y * 2.0 + w * 5.0 + t + uBass * 0.6);
    float g = sin(p.z * 2.5 - p.y * 1.5 + w * 4.0 - t * 0.7);
    float fold = f * 0.6 + g * 0.4;

    vec3 a = vec3(0.55, 0.35, 0.75);
    vec3 b = vec3(0.95, 0.65, 0.8);
    vec3 col = mix(a, b, fold * 0.5 + 0.5) * 0.55;
    float sheen = pow(abs(fold), 10.0);
    col += vec3(1.0, 0.95, 1.0) * sheen * (0.5 + uHigh * 0.6);
    col *= 0.6 + 0.4 * smoothstep(-0.4, 0.6, d.y);
    return col;
}
