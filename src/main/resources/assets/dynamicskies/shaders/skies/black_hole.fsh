// Black Hole: lensed stars, photon ring and a swirling accretion disk.

vec3 background(vec3 d) {
    vec3 col = vec3(0.004, 0.003, 0.012);
    col += vec3(0.25, 0.08, 0.35) * pow(fbm3(d * 3.0), 3.0) * 0.8;
    col += stars(d, 220.0, 0.9);
    col += stars(d, 70.0, 0.98) * vec3(1.0, 0.9, 0.8);
    return col;
}

vec3 sky(vec3 d) {
    vec3 f = normalize(vec3(0.35, 0.4, -1.0));
    vec3 rgt = normalize(cross(f, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rgt, f);
    float fd = dot(d, f);
    if (fd <= 0.05) {
        return background(d);
    }

    vec2 uv = vec2(dot(d, rgt), dot(d, up)) / fd;
    float r = length(uv);
    float rs = 0.11 * (1.0 + uBass * 0.15);

    // Bend light around the hole.
    vec2 bent = uv - normalize(uv + 1e-5) * rs * rs * 2.2 / max(r, 1e-3);
    vec3 bd = normalize(f + rgt * bent.x + up * bent.y);
    vec3 col = background(bd);

    // Tilted accretion disk.
    float tilt = 0.22;
    vec2 e = vec2(uv.x, uv.y / tilt);
    float rd = length(e);
    vec2 en = e / max(rd, 1e-4);
    float spin = uTime * 1.2;
    vec2 rot = vec2(en.x * cos(spin) - en.y * sin(spin), en.x * sin(spin) + en.y * cos(spin));
    float swirl = noise(rot * 3.0 + rd * 9.0) + 0.5 * noise(rot * 9.0 + vec2(rd * 30.0));
    float band = smoothstep(rs * 1.5, rs * 2.0, rd) * smoothstep(rs * 5.5, rs * 2.6, rd);
    float doppler = 1.0 + 0.7 * (-en.x);
    vec3 hot = mix(vec3(1.0, 0.35, 0.08), vec3(1.0, 0.92, 0.75), smoothstep(rs * 4.0, rs * 1.6, rd));
    vec3 disk = hot * band * (0.4 + swirl * 0.8) * doppler * (1.2 + uLevel * 1.2);

    // Image of the far side of the disk arching over the top.
    float arc = smoothstep(rs * 1.1, rs * 1.35, r) * smoothstep(rs * 2.2, rs * 1.45, r) * smoothstep(-0.02, 0.06, uv.y);
    disk += hot * arc * 0.9 * (0.6 + 0.4 * swirl);

    float photon = exp(-abs(r - rs * 1.05) * 90.0) * (1.0 + uBeat * 1.5);
    col += vec3(1.0, 0.8, 0.6) * photon;

    float hole = smoothstep(rs, rs * 0.96, r);
    col *= 1.0 - hole;
    col += disk * (e.y < 0.0 ? 1.0 : 1.0 - hole);
    return col;
}
