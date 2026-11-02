// The Watcher: an enormous eye opens in a storm-red sky. It blinks, glances around,
// and its pupil widens with the bass.

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.3, 0.05, 0.06), vec3(0.04, 0.0, 0.02), pow(y, 0.6));

    // Swirling clouds circling the eye.
    vec3 ed = normalize(vec3(0.0, 0.45, -1.0));
    vec3 rgt = normalize(cross(ed, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rgt, ed);
    float fd = dot(d, ed);
    vec2 uv = vec2(dot(d, rgt), dot(d, up)) / max(fd, 0.05);
    float r = length(uv);
    float ang = atan(uv.y, uv.x) + 0.6 / (r + 0.3) - uTime * 0.08;
    float swirl = fbm(vec2(cos(ang), sin(ang)) * (1.5 + r) + r * 2.0);
    col = mix(col, vec3(0.12, 0.01, 0.02), smoothstep(0.45, 0.75, swirl) * 0.8);
    col += vec3(0.6, 0.05, 0.05) * exp(-r * 2.5) * 0.4 * (1.0 + uLevel);

    if (fd <= 0.05) return col;

    // Blink: eyelids close briefly every few seconds.
    float cycle = mod(uTime, 7.0);
    float blink = smoothstep(0.0, 0.12, abs(cycle - 6.6) - 0.02);
    float openH = 0.42 * blink + 0.01;
    float lid = openH * (1.0 - pow(abs(uv.x) / 0.9, 2.0));
    float inEye = step(abs(uv.x), 0.9) * step(abs(uv.y), lid);

    // Glancing around.
    vec2 look = vec2(sin(uTime * 0.23) * 0.18 + sin(uTime * 0.71) * 0.05, cos(uTime * 0.31) * 0.06);
    vec2 iv = uv - look;
    float ir = length(iv);
    float irisR = 0.32;
    float pupilR = 0.09 + uBass * 0.05 + uBeat * 0.02;

    vec3 sclera = mix(vec3(0.85, 0.75, 0.7), vec3(0.6, 0.3, 0.3), smoothstep(0.3, 0.9, length(uv)));
    float veins = smoothstep(0.55, 0.6, fbm(uv * 9.0)) * smoothstep(0.35, 0.8, length(uv));
    sclera = mix(sclera, vec3(0.6, 0.05, 0.05), veins * 0.6);

    float ia = atan(iv.y, iv.x);
    float fibers = fbm(vec2(ia * 6.0, ir * 12.0));
    vec3 iris = mix(vec3(0.9, 0.55, 0.05), vec3(0.6, 0.08, 0.02), smoothstep(pupilR, irisR, ir));
    iris *= 0.6 + 0.6 * fibers;
    iris += vec3(1.0, 0.8, 0.3) * exp(-abs(ir - pupilR) * 60.0) * 0.6;

    vec3 eye = sclera;
    eye = mix(eye, iris, smoothstep(irisR, irisR - 0.01, ir));
    eye = mix(eye, vec3(0.0), smoothstep(pupilR, pupilR - 0.01, ir));
    eye += vec3(1.0) * smoothstep(0.035, 0.02, length(iv - vec2(0.08, 0.08))) * 0.8;  // glint
    eye *= 0.55 + 0.45 * smoothstep(0.0, lid, lid - abs(uv.y));                      // shadow under lids

    float lidEdge = exp(-abs(abs(uv.y) - lid) * 80.0) * step(abs(uv.x), 0.9);
    col = mix(col, eye, inEye);
    col = mix(col, vec3(0.05, 0.0, 0.0), lidEdge * 0.8);
    return col;
}
