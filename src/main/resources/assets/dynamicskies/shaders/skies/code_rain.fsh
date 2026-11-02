// Code Rain: falling green glyph columns all around you.

float glyph(vec2 id, vec2 f, float seed) {
    vec2 m = (f - 0.12) / 0.76;
    if (m.x < 0.0 || m.x > 1.0 || m.y < 0.0 || m.y > 1.0) return 0.0;
    vec2 px = floor(m * vec2(4.0, 6.0));
    return step(0.5, hash12(px + id * 7.13 + seed * 3.1));
}

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x) / TAU + 0.5;
    float el = asin(clamp(d.y, -1.0, 1.0)) / PI + 0.5;
    float cols = 180.0;
    float rows = 90.0;
    vec2 g = vec2(az * cols, el * rows);
    vec2 id = floor(g);
    vec2 f = fract(g);

    float h = hash11(id.x * 1.37 + 7.0);
    float speed = 7.0 + h * 12.0;
    float span = rows * 1.6;
    float head = rows - mod(uTime * speed * (1.0 + uBass * 0.6) + h * span * 3.0, span);
    float dist = id.y - head;
    float len = 10.0 + h * 25.0 + uLevel * 10.0;

    float b = (dist >= 0.0 && dist < len) ? 1.0 - dist / len : 0.0;
    float change = floor(uTime * (2.0 + h * 4.0) + hash11(id.y));
    float on = glyph(id, f, change);

    vec3 col = vec3(0.0, 0.02, 0.008);
    col += vec3(0.1, 1.0, 0.35) * on * b * b * (0.8 + uMid * 0.6);
    col += vec3(0.8, 1.0, 0.85) * on * step(dist, 0.9) * step(0.0, dist) * (1.0 + uBeat);
    col *= smoothstep(0.995, 0.9, abs(d.y));
    return col;
}
