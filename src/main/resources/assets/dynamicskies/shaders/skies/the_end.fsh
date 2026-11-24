// The End: a purple void with end rods drifting up, obsidian pillars on the horizon
// topped with glowing crystals, and now and then the dragon gliding past as a dark outline.

float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

float sdTri(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c;
    vec2 v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 dd = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                      vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                      vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(dd.x) * sign(dd.y);
}

// The dragon side-on, facing +x, about 1 unit long. flap goes -1..1.
float dragon(vec2 p, float flap, float t) {
    // Body and neck.
    float d = length((p - vec2(0.0, 0.0)) * vec2(1.0, 2.6)) / 2.6 - 0.07;
    d = min(d, sdSeg(p, vec2(0.12, 0.02), vec2(0.3, 0.07)) - 0.03);
    // Head with a little snout.
    d = min(d, length((p - vec2(0.34, 0.07)) * vec2(1.0, 1.6)) / 1.6 - 0.045);
    d = min(d, sdSeg(p, vec2(0.34, 0.07), vec2(0.43, 0.05)) - 0.02);
    // Tail: a few segments swaying.
    vec2 a = vec2(-0.1, 0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        vec2 b = a + vec2(-0.11, 0.025 * sin(t * 2.0 - fi * 0.9));
        d = min(d, sdSeg(p, a, b) - (0.04 - fi * 0.007));
        a = b;
    }
    // Two wings, the far one a little behind.
    vec2 tip = vec2(-0.12, 0.45 * flap);
    d = min(d, sdTri(p, vec2(0.1, 0.02), vec2(-0.12, 0.02), tip));
    d = min(d, sdTri(p, vec2(0.0, 0.02), vec2(-0.32, 0.0), tip + vec2(-0.1, -0.05)));
    vec2 tip2 = vec2(-0.08, 0.38 * flap + 0.04);
    d = min(d, sdTri(p, vec2(0.12, 0.03), vec2(-0.08, 0.03), tip2));
    return d;
}

float endRods(float az, float el, float n, float speed, float seed) {
    float u = (az / TAU + 0.5) * n;
    float v = el / TAU * n + uTime * speed;
    vec2 id = floor(vec2(u, v));
    vec2 f = fract(vec2(u, v)) - 0.5;
    float h = hash12(vec2(mod(id.x, n), id.y) + seed);
    if (h < 0.86) return 0.0;
    vec2 o = (hash22(vec2(mod(id.x, n), id.y) + seed) - 0.5) * 0.5;
    o.x += sin(uTime * 0.5 + h * 40.0) * 0.08;
    vec2 q = vec2((f.x - o.x) * cos(el), f.y - o.y);
    float rod = exp(-abs(q.x) * 30.0) * smoothstep(0.2, 0.05, abs(q.y)) * 1.5;
    float glow = exp(-length(q * vec2(1.0, 0.6)) * 12.0) * 0.3;
    float tw = 0.6 + 0.4 * sin(uTime * (1.5 + h * 3.0) + h * 30.0);
    return (rod + glow) * tw;
}

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x);
    float el = elevation(d);

    // The void: dark purple with slow swirling murk and a faint pale haze near the horizon.
    float m = fbm(vec3(d * 2.5 + vec3(0.0, uTime * 0.01, 0.0)));
    float m2 = fbm(vec3(d * 6.0 - vec3(uTime * 0.015, 0.0, 0.0)));
    vec3 col = vec3(0.04, 0.02, 0.06);
    col += vec3(0.14, 0.06, 0.2) * smoothstep(0.35, 0.8, m) * (1.0 + uBass * 0.4);
    col += vec3(0.06, 0.08, 0.06) * smoothstep(0.55, 0.85, m2) * 0.6;
    col += vec3(0.12, 0.08, 0.14) * exp(-abs(el) * 7.0);
    col += vec3(0.6, 0.9, 0.85) * stars(d, 200.0, 0.97) * 0.4;

    // Obsidian pillars around the horizon, each with a crystal on top.
    float pu = (az / TAU + 0.5) * 10.0;
    float pid = floor(pu);
    float pf = fract(pu) - 0.5 - (hash11(mod(pid, 10.0) * 5.1) - 0.5) * 0.5;
    float ph = hash11(mod(pid, 10.0) * 3.3 + 1.0);
    float pTop = 0.05 + ph * 0.12;
    float pw = 0.025 + hash11(mod(pid, 10.0) + 9.0) * 0.02;
    float px = pf * TAU / 10.0 / max(cos(el), 0.3);
    if (abs(px) < pw && el > -0.02 && el < pTop) {
        col = vec3(0.02, 0.01, 0.035);
    }
    vec2 cq = vec2(px, el - pTop - 0.012);
    float crystal = exp(-length(cq) * 140.0);
    float spin = 0.7 + 0.3 * sin(uTime * 2.0 + pid);
    col += vec3(1.0, 0.55, 0.85) * (crystal * 1.6 + exp(-length(cq) * 30.0) * 0.12) * spin * (1.0 + uBeat * 0.8);

    // End rods drifting upward, with a soft pale glow.
    float rods = endRods(az, el, 140.0, -0.05, 0.0) + endRods(az, el, 90.0, -0.035, 7.0) * 0.7;
    col += vec3(0.95, 0.9, 1.0) * rods * (0.8 + uHigh * 0.6) * smoothstep(-0.1, 0.1, el) * smoothstep(1.4, 1.0, el);

    // The dragon: one pass about every 50 seconds.
    float period = 50.0;
    float pass = floor(uTime / period);
    float lt = uTime - pass * period;
    float dur = 16.0;
    if (lt < dur) {
        float side = hash11(pass) < 0.5 ? -1.0 : 1.0;
        float k = lt / dur;
        float dAz = hash11(pass + 3.0) * TAU + side * (k - 0.5) * 2.0;
        float dEl = 0.35 + hash11(pass + 5.0) * 0.35 + 0.05 * sin(lt * 0.7);
        vec3 c = vec3(cos(dEl) * cos(dAz), sin(dEl), cos(dEl) * sin(dAz));
        float dc = dot(d, c);
        if (dc > 0.5) {
            vec3 t1 = normalize(cross(vec3(0.0, 1.0, 0.0), c));
            vec3 t2 = cross(c, t1);
            vec2 p = vec2(dot(d, t1), dot(d, t2)) / dc;
            p.x *= -side;
            p /= 0.42;
            float flap = sin(lt * 2.4);
            float sd = dragon(p, flap, lt);
            float fade = smoothstep(0.0, 1.5, lt) * smoothstep(dur, dur - 1.5, lt);
            float body = smoothstep(0.01, -0.01, sd);
            float rim = exp(-abs(sd) * 60.0);
            col = mix(col, vec3(0.01, 0.0, 0.015), body * fade);
            col += vec3(0.55, 0.2, 0.8) * rim * fade * 0.5;
            // Glowing eyes.
            float eye = exp(-length(p - vec2(0.36, 0.085)) * 220.0);
            col += vec3(1.0, 0.3, 1.0) * eye * fade * 3.0;
        }
    }

    if (el < 0.0) {
        col *= exp(el * 4.0);
        col += vec3(0.05, 0.02, 0.07) * exp(el * 20.0);
    }
    return col;
}
