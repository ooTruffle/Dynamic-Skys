// Fireworks: a shell launches on each beat and bursts over a dark night.
// The bass at launch sets the burst size. Shells come from Fireworks.java.

uniform vec4 uShells[8];    // xyz = burst direction, w = age in seconds (< 0 = unused)
uniform vec4 uShellInfo[8]; // x = hue, y = size, z = seed, w = rise time

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-8), 0.0, 1.0);
    return length(pa - ba * h);
}

// One ring of sparks. n = spark count, r = how far they have flown, rt = where the tails start.
vec3 sparkRing(vec2 q, float n, float r, float rt, float seed, vec3 col, float crackle, float b) {
    float sector = (atan(q.y, q.x) / TAU) * n;
    float k0 = floor(sector);
    vec3 acc = vec3(0.0);
    for (int j = -1; j <= 1; j++) {
        float k = k0 + float(j);
        float kk = mod(k, n);
        float h = hash11(kk * 7.13 + seed);
        float a = (k + 0.5 + (h - 0.5) * 0.7) / n * TAU;
        float sp = 0.7 + 0.45 * hash11(kk * 3.71 + seed * 1.3);
        vec2 dir = vec2(cos(a), sin(a));
        float dist = segDist(q, dir * rt * sp, dir * r * sp);
        float head = length(q - dir * r * sp);
        float tw = mix(1.0, step(0.45, hash11(kk + floor(uTime * 18.0) * 1.7 + seed)), crackle * smoothstep(0.6, 1.3, b));
        acc += col * (exp(-dist * 380.0) * 1.6 + exp(-dist * 70.0) * 0.15) * tw;
        acc += mix(vec3(1.0), col, smoothstep(0.0, 0.6, b)) * exp(-head * 650.0) * 1.2 * tw;
    }
    return acc;
}

vec3 shellLight(vec3 d, vec4 s, vec4 inf, inout vec3 skyGlow) {
    float age = s.w;
    if (age < 0.0) return vec3(0.0);
    vec3 c = s.xyz;
    float rise = inf.w;
    float size = inf.y;
    float seed = inf.z;
    vec3 col = hsv2rgb(vec3(inf.x, 0.7, 1.0));

    float dc = dot(d, c);
    if (dc < 0.3) {
        if (age > rise) skyGlow += col * 0.02 * size * exp(-(age - rise) * 3.0);
        return vec3(0.0);
    }
    vec3 t1 = normalize(cross(vec3(0.0, 1.0, 0.0), c));
    vec3 t2 = cross(c, t1);
    vec2 p = vec2(dot(d, t1), dot(d, t2)) / dc;

    if (age < rise) {
        // The rocket climbing from the horizon, with a fizzing trail.
        vec3 h = normalize(vec3(c.x, 0.0, c.z));
        float y0 = dot(h, t2) / dot(h, c);
        float k = age / rise;
        float y = mix(y0, 0.0, 1.0 - (1.0 - k) * (1.0 - k));
        float wig = sin(age * 23.0 + seed) * 0.004;
        vec2 head = vec2(wig, y);
        float tailLen = 0.12;
        float dist = segDist(p, head - vec2(0.0, tailLen), head);
        float along = clamp((p.y - (y - tailLen)) / tailLen, 0.0, 1.0);
        vec3 spark = vec3(1.0, 0.75, 0.45);
        return spark * (exp(-dist * 500.0) * along * 0.6 + exp(-length(p - head) * 400.0) * 1.3);
    }

    float b = age - rise;
    float grow = 1.0 - exp(-b * 3.2);
    float R = 0.24 * size * grow;
    float Rt = R * (0.5 + 0.45 * smoothstep(0.0, 2.0, b)); // tails shorten as sparks slow
    float fade = 1.0 - smoothstep(1.0, 2.5, b);
    vec2 q = p + vec2(0.0, 0.03 * b * b); // gravity pulls the sparks down

    float crackle = step(0.5, fract(seed * 0.37));
    vec3 light = sparkRing(q, 40.0, R, Rt, seed, col, crackle, b);
    if (fract(seed * 0.61) > 0.45) {
        vec3 col2 = hsv2rgb(vec3(inf.x + 0.45, 0.6, 1.0));
        light += sparkRing(q, 16.0, R * 0.5, Rt * 0.5, seed + 11.0, col2, 1.0 - crackle, b) * 0.9;
    }

    // Bright flash at the moment of the burst, and smoke lit by it.
    float flash = exp(-b * 7.0) * size;
    light += mix(vec3(1.0), col, 0.4) * exp(-length(q) * 14.0) * flash;
    light += col * 0.06 * exp(-length(q) / max(R, 0.02) * 1.5) * fade;
    skyGlow += col * 0.02 * size * exp(-b * 3.0);
    return light * fade;
}

vec3 sky(vec3 d) {
    float el = elevation(d);
    vec3 col = mix(vec3(0.035, 0.03, 0.08), vec3(0.004, 0.006, 0.02), smoothstep(0.0, 0.8, d.y));
    col += vec3(0.08, 0.05, 0.08) * exp(-abs(el) * 9.0);
    col += stars(d, 160.0, 0.95) * 0.6 * smoothstep(0.05, 0.4, d.y);

    // Faint drifting smoke left behind by earlier shells.
    float smoke = fbm(d * 3.0 + vec3(uTime * 0.02, 0.0, 0.0));
    col += vec3(0.03, 0.025, 0.035) * smoothstep(0.45, 0.8, smoke) * smoothstep(-0.05, 0.3, el);

    vec3 glow = vec3(0.0);
    vec3 light = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        light += shellLight(d, uShells[i], uShellInfo[i], glow);
    }
    col += glow * (0.4 + smoke);

    if (d.y < 0.0) {
        // Dark ground with a soft reflection of the light above.
        col = vec3(0.01, 0.01, 0.02) + glow * 0.5 * exp(d.y * 6.0);
        col += vec3(0.05, 0.035, 0.05) * exp(d.y * 30.0);
        return col;
    }
    return col + light;
}
