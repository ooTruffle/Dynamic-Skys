// Spectrum: an audio visualizer — bars around the horizon and a ring overhead.

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x);
    float x = abs(az) / PI;
    float el = elevation(d);
    float r = acos(clamp(d.y, -1.0, 1.0));

    float bars = 64.0;
    float bi = floor(x * bars);
    float fx = fract(x * bars);
    float val = spectrum((bi + 0.5) / bars);
    float gap = step(0.15, fx) * step(fx, 0.85);
    vec3 hue = hsv2rgb(vec3(bi / bars * 0.8 + uTime * 0.03, 0.75, 1.0));

    vec3 col = mix(vec3(0.02, 0.01, 0.05), vec3(0.0), smoothstep(0.0, 1.0, abs(d.y)));
    col += hue * 0.15 * exp(-abs(el) * 6.0) * (0.3 + uBass);

    float h = 0.02 + val * 0.55;
    if (el > 0.0 && el < h) {
        col = mix(col, hue * (0.5 + 0.8 * el / h), gap);
    }
    col += hue * gap * exp(-max(el - h, 0.0) * 40.0) * step(0.0, el - h) * 0.4;
    if (el < 0.0 && -el < h * 0.6) {
        col += hue * gap * 0.25 * (1.0 + el / (h * 0.6));
    }

    float inner = 0.22 + uBass * 0.04;
    float rl = val * 0.35;
    if (r > inner && r < inner + rl) {
        col += hue * gap * (0.6 + uBeat * 0.6);
    }
    col += vec3(0.6, 0.5, 1.0) * exp(-abs(r - inner) * 80.0) * (0.4 + uBeat);
    return col;
}
