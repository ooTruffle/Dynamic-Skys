// Pixel Sunset: a blocky sunset over the sea in a small dithered palette, to match the game's look.
// Everything is worked out per big "pixel", then snapped to the palette with ordered dithering.

float bayer2(vec2 a) {
    a = floor(a);
    return fract(dot(a, vec2(0.5, a.y * 0.75)));
}

float bayer4(vec2 a) {
    return bayer2(0.5 * a) * 0.25 + bayer2(a);
}

vec3 skyPal(float i) {
    if (i < 0.5) return vec3(1.0, 0.86, 0.45);
    if (i < 1.5) return vec3(1.0, 0.6, 0.28);
    if (i < 2.5) return vec3(0.93, 0.38, 0.42);
    if (i < 3.5) return vec3(0.58, 0.24, 0.5);
    if (i < 4.5) return vec3(0.27, 0.13, 0.38);
    if (i < 5.5) return vec3(0.13, 0.07, 0.24);
    return vec3(0.06, 0.035, 0.14);
}

vec3 seaPal(float i) {
    if (i < 0.5) return vec3(0.55, 0.3, 0.45);
    if (i < 1.5) return vec3(0.3, 0.17, 0.36);
    if (i < 2.5) return vec3(0.16, 0.1, 0.27);
    return vec3(0.07, 0.05, 0.16);
}

// Snap a 0..n value to a palette index, dithered.
float dither(float v, float n, float b) {
    float x = clamp(v, 0.0, 1.0) * n;
    return floor(x) + step(b, fract(x));
}

vec3 sky(vec3 d) {
    float N = 360.0;
    float px = TAU / N;
    float az = atan(d.z, d.x);
    float el = elevation(d);

    vec2 cell = vec2(floor((az / TAU + 0.5) * N), floor(el / px));
    float caz = (cell.x + 0.5) / N * TAU - PI;
    float cel = (cell.y + 0.5) * px;
    vec3 cd = vec3(cos(cel) * cos(caz), sin(cel), cos(cel) * sin(caz));
    float b = bayer4(cell);
    float tq = floor(uTime * 6.0) / 6.0;

    // Sun low in the west.
    vec3 sunDir = normalize(vec3(-1.0, 0.1, 0.0));
    float sr = acos(clamp(dot(cd, sunDir), -1.0, 1.0));
    float sunR = 0.1 + min(uBass, 1.5) * 0.012;

    if (cel >= 0.0) {
        float g = pow(cel / (PI * 0.5), 0.55);
        g -= exp(-sr * 2.2) * 0.35;
        vec3 col = skyPal(dither(g, 6.0, b));

        // Blocky clouds in two tones, lit on the side facing the sun.
        vec3 cp = vec3(ring(cd, 2.2), tq * 0.02);
        float n = fbm(cp + vec3(0.0, 0.0, cel * 9.0));
        float band = smoothstep(0.03, 0.12, cel) * (1.0 - smoothstep(0.3, 0.5, cel));
        float cl = n * band;
        if (cl > 0.42) {
            float lit = fbm(cp + vec3(0.0, 0.0, (cel - px * 1.5) * 9.0)) * band;
            float warm = exp(-sr * 1.3);
            col = lit < 0.42 ? mix(vec3(1.0, 0.55, 0.4), vec3(1.0, 0.8, 0.5), step(b, warm))
                             : mix(vec3(0.42, 0.18, 0.4), vec3(0.65, 0.3, 0.45), step(b, warm * 1.5));
        }

        // Single-pixel stars up high.
        float h = hash12(cell);
        if (cel > 0.7 && h > 0.985) {
            float tw = step(0.3, fract(h * 37.0 + floor(uTime * 2.0 + h * 5.0) * 0.618));
            col = mix(col, vec3(1.0, 0.95, 0.85), tw * smoothstep(0.7, 1.0, cel));
        }

        // The sun: two rings of colour and a dithered halo.
        if (sr < sunR) {
            col = sr < sunR * 0.65 ? vec3(1.0, 0.97, 0.75) : vec3(1.0, 0.85, 0.45);
            col *= 1.0 + uBeat * 0.15;
        } else if (sr < sunR + px * 2.0 && b < 0.5) {
            col = vec3(1.0, 0.8, 0.45);
        }
        return col;
    }

    // Sea: banded blues with a shimmering column of sun glints.
    float depth = clamp(-cel / 0.6, 0.0, 1.0);
    vec3 col = seaPal(dither(pow(depth, 0.5) + 0.12 * (1.0 - exp(-sr)) - 0.1, 3.0, b));
    float wave = hash12(vec2(cell.x * 0.37 + floor(tq * 2.0), cell.y));
    float dAz = abs(mod(caz, TAU) - PI); // distance from the sun's azimuth
    float column = exp(-dAz * dAz / (0.012 + depth * 0.03));
    if (wave < column * (0.55 + uBeat * 0.2)) {
        col = wave < column * 0.2 ? vec3(1.0, 0.9, 0.6) : vec3(0.95, 0.55, 0.4);
    }
    // A thin bright line where the sea meets the sky.
    if (cell.y == -1.0) col = mix(col, vec3(0.9, 0.5, 0.45), 0.6);
    return col;
}
