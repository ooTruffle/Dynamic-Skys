// Circuit Maze: glowing circuit-board traces with pulses running along them.

vec3 sky(vec3 d) {
    vec3 a = abs(d);
    vec2 uv;
    float face;
    if (a.x >= a.y && a.x >= a.z) { uv = d.yz / a.x; face = d.x > 0.0 ? 1.0 : 2.0; }
    else if (a.y >= a.z) { uv = d.xz / a.y; face = d.y > 0.0 ? 3.0 : 4.0; }
    else { uv = d.xy / a.z; face = d.z > 0.0 ? 5.0 : 6.0; }

    vec2 g = uv * 14.0;
    vec2 id = floor(g) + face * 100.0;
    vec2 f = fract(g);
    if (hash12(id) > 0.5) f.x = 1.0 - f.x;

    float w = fwidth(g.x) * 1.5 + 0.04;
    float line = smoothstep(w, 0.0, abs(f.x - f.y));
    float node = smoothstep(0.12, 0.08, length(f - 0.5)) * step(0.85, hash12(id + 3.3));

    float pulse = 0.5 + 0.5 * sin((id.x + id.y) * 0.7 - uTime * 3.0 * (1.0 + uBass * 0.6));
    pulse = pow(pulse, 6.0);

    vec3 col = vec3(0.0, 0.02, 0.025);
    col += vec3(0.0, 0.45, 0.4) * line * 0.6;
    col += vec3(0.3, 1.0, 0.85) * line * pulse * (1.0 + uMid);
    col += vec3(0.6, 1.0, 0.9) * node * (0.6 + uBeat);
    return col;
}
