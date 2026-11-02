// Cherry Blossom: pastel spring sky with drifting pink petals.

float petals(vec3 d, float scale, float speed, float seed) {
    float az = atan(d.z, d.x) / TAU + 0.5;
    float el = elevation(d) / PI + 0.5;
    vec2 g = vec2(az * scale * 2.0, el * scale + uTime * speed);
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash12(id + seed);
    if (h < 0.55) return 0.0;
    vec2 o = (hash22(id + seed) - 0.5) * 0.5;
    o.x += sin(uTime * (1.0 + h) + h * 20.0) * 0.15;
    vec2 q = f - o;
    float ang = uTime * (0.5 + h) + h * 10.0;
    q = vec2(q.x * cos(ang) - q.y * sin(ang), q.x * sin(ang) + q.y * cos(ang));
    float e = length(q * vec2(1.0, 1.8));
    return smoothstep(0.12, 0.08, e);
}

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(1.0, 0.85, 0.88), vec3(0.6, 0.75, 0.95), smoothstep(0.0, 0.7, y));

    if (d.y > 0.0) {
        vec2 p = d.xz / (d.y + 0.1) * 0.5 + vec2(uTime * 0.01, 0.0);
        float cl = smoothstep(0.5, 0.8, fbm(p)) * smoothstep(0.0, 0.15, d.y);
        col = mix(col, vec3(1.0, 0.95, 0.97), cl * 0.7);
    }

    float boost = 1.0 + uBass * 0.5 + uBeat * 0.3;
    col = mix(col, vec3(1.0, 0.6, 0.75), petals(d, 40.0, -1.2 - uLevel, 1.0) * 0.9);
    col = mix(col, vec3(1.0, 0.72, 0.82) * boost, petals(d, 22.0, -0.9 - uLevel, 7.0));
    return col;
}
