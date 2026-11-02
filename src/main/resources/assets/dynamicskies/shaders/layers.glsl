
// Optional layers drawn on top of every sky (added after the sky file).
// Names are prefixed with layer_ so they can't clash with a sky's own functions.

uniform float uLayerStars;
uniform float uLayerAurora;
uniform float uLayerWisps;
uniform vec4 uMeteors[4];     // xyz = start direction, w = age in seconds (< 0 = unused)
uniform vec4 uMeteorDirs[4];  // xyz = direction of travel, w = brightness

float layer_auroraBand(vec2 p, float t) {
    float n = fbm3(vec3(p.x * 0.6 + t * 0.05, p.y * 0.25 - t * 0.02, t * 0.03));
    float band = abs(sin(p.x * 0.7 + n * 6.0 + t * 0.12));
    return pow(1.0 - band, 5.0) * smoothstep(0.25, 0.75, n);
}

vec3 layer_aurora(vec3 d) {
    if (d.y <= 0.0) return vec3(0.0);
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 10; i++) {
        float fi = float(i) / 10.0;
        vec2 p = d.xz / (d.y + 0.2) * (0.8 + fi * 0.7) * 1.6;
        float a = layer_auroraBand(p, uTime + 17.0);
        acc += mix(vec3(0.1, 1.0, 0.45), vec3(0.6, 0.15, 1.0), fi) * a * (1.0 - fi) * 0.24;
    }
    return acc * (1.0 + uBass * 1.6 + uBeat * 0.8) * smoothstep(0.0, 0.18, d.y);
}

float layer_wisps(vec3 d) {
    if (d.y <= 0.0) return 0.0;
    vec2 p = d.xz / (d.y + 0.15);
    p = vec2(p.x * 0.35, p.y * 1.6) + vec2(uTime * 0.03, 0.0);
    float n = fbm(p + fbm(p * 0.5) * 1.5);
    return smoothstep(0.5, 0.8, n) * smoothstep(0.0, 0.2, d.y);
}

// A shooting star: a streak moving along a great circle from `start` toward `dir`.
vec3 layer_meteor(vec3 d, vec3 start, vec3 dir, float age) {
    float life = 1.1;
    if (age > life) return vec3(0.0);
    float facing = dot(d, start);
    if (facing < 0.5) return vec3(0.0);

    // Great circles are straight lines in this projection, so the path is a line.
    vec3 q = d / facing - start;
    float along = dot(q, dir);
    float perp = length(q - dir * along);
    float head = tan(min(age * 0.9, 1.2));
    float tail = head - 0.2;
    float t = clamp((along - tail) / (head - tail), 0.0, 1.0);
    float dist = along > head ? length(vec2(along - head, perp))
               : along < tail ? length(vec2(along - tail, perp))
               : perp;
    float fade = smoothstep(0.0, 0.08, age) * smoothstep(life, life * 0.55, age);
    float core = exp(-dist * 1100.0) * t * t;
    float glow = exp(-dist * 260.0) * t * 0.25;
    float spark = exp(-length(vec2(along - head, perp)) * 500.0);
    return vec3(1.0, 0.95, 0.85) * (core + glow + spark * 1.5) * fade;
}

vec3 applyLayers(vec3 c, vec3 d) {
    if (uLayerWisps > 0.0) {
        float w = layer_wisps(d) * uLayerWisps;
        float l = dot(c, vec3(0.299, 0.587, 0.114));
        vec3 wc = vec3(0.85, 0.88, 0.95) * clamp(l * 1.6 + 0.12, 0.15, 1.0);
        c = mix(c, wc, w * 0.7);
    }
    if (uLayerStars > 0.0) {
        float s = stars(d, 260.0, 0.9) + stars(d, 110.0, 0.965) * 0.8;
        c += vec3(s) * uLayerStars * smoothstep(-0.05, 0.2, d.y);
    }
    if (uLayerAurora > 0.0) {
        c += layer_aurora(d) * uLayerAurora;
    }
    for (int i = 0; i < 4; i++) {
        vec4 m = uMeteors[i];
        if (m.w >= 0.0) {
            c += layer_meteor(d, m.xyz, uMeteorDirs[i].xyz, m.w) * uMeteorDirs[i].w;
        }
    }
    return c;
}
