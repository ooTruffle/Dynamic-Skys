// Eclipse: a black disk ringed by a streaming white-gold corona.

vec3 sky(vec3 d) {
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.08, 0.1, 0.2), vec3(0.005, 0.008, 0.025), pow(y, 0.4));
    col += stars(d, 210.0, 0.93) * 0.8;

    vec3 ed = normalize(vec3(0.2, 0.55, 0.8));
    vec3 rgt = normalize(cross(ed, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rgt, ed);
    float r = acos(clamp(dot(d, ed), -1.0, 1.0));
    vec2 dir = normalize(vec2(dot(d, rgt), dot(d, up)) + 1e-5);
    float rad = 0.12;

    float outer = max(r - rad, 0.0);
    float streaks = fbm(dir * 5.0 + vec2(outer * 6.0 - uTime * 0.15)) ;
    float corona = exp(-outer * (14.0 - uLevel * 5.0)) * (0.5 + streaks * 1.2);
    corona += exp(-outer * 60.0) * 1.5;
    col += vec3(1.0, 0.9, 0.7) * corona * (1.0 + uBeat * 0.6);

    // Diamond ring.
    vec2 bead = normalize(vec2(cos(uTime * 0.05), sin(uTime * 0.05)));
    float b = pow(max(dot(dir, bead), 0.0), 200.0) * exp(-abs(r - rad) * 120.0);
    col += vec3(1.0, 0.95, 0.85) * b * 6.0;

    col *= 1.0 - smoothstep(rad, rad - 0.002, r);
    return col;
}
