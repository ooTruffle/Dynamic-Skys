// Megastructure: a colossal ring arcing across a starfield, with a gas giant behind it.

vec3 megaPlanet(vec3 d) {
    vec3 pd = normalize(vec3(-0.6, 0.35, -0.7));
    float r = acos(clamp(dot(d, pd), -1.0, 1.0));
    float rad = 0.32;
    if (r > rad) {
        return vec3(0.25, 0.35, 0.6) * exp(-(r - rad) * 25.0) * 0.4;
    }
    vec3 rgt = normalize(cross(pd, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rgt, pd);
    vec2 uv = vec2(dot(d, rgt), dot(d, up)) / rad;
    float bands = fbm(vec2(uv.y * 6.0 + fbm(uv * 3.0 + uTime * 0.01) * 1.5, uv.x * 0.5));
    vec3 col = mix(vec3(0.75, 0.55, 0.4), vec3(0.95, 0.85, 0.7), bands);
    col = mix(col, vec3(0.6, 0.3, 0.2), smoothstep(0.6, 0.8, bands));
    float lit = clamp(dot(normalize(vec3(uv, sqrt(max(0.0, 1.0 - dot(uv, uv))))), normalize(vec3(0.7, 0.3, 0.6))), 0.0, 1.0);
    return col * (0.08 + lit * 0.9);
}

vec3 sky(vec3 d) {
    vec3 col = vec3(0.004, 0.005, 0.012);
    col += stars(d, 260.0, 0.9) + stars(d, 90.0, 0.975) * vec3(0.9, 0.95, 1.0);
    col += megaPlanet(d);

    // The ring: a band around a tilted great circle.
    vec3 n = normalize(vec3(1.0, 0.3, 0.35));
    float lat = dot(d, n);
    float width = 0.15;
    vec3 a1 = normalize(cross(n, vec3(0.0, 0.0, 1.0)));
    vec3 a2 = cross(n, a1);
    float along = atan(dot(d, a2), dot(d, a1));

    float inner = abs(lat);
    if (inner < width) {
        float v = lat / width;                       // -1 .. 1 across the ring
        float u = along * 40.0;                      // panels along the ring
        vec2 cell = floor(vec2(u, v * 4.0));
        vec2 f = fract(vec2(u, v * 4.0));
        float panel = 0.55 + 0.45 * hash12(cell);
        float seam = smoothstep(0.0, 0.06, f.x) * smoothstep(1.0, 0.94, f.x) * smoothstep(0.0, 0.1, f.y) * smoothstep(1.0, 0.9, f.y);
        float shade = 0.35 + 0.65 * smoothstep(-1.0, 1.0, v);   // lit from one edge
        vec3 hull = vec3(0.55, 0.58, 0.65) * panel * seam * shade;

        // Inner surface: a strip of land and sea along the middle.
        float land = smoothstep(0.35, 0.1, abs(v));
        float terrain = fbm(vec2(along * 40.0, v * 3.0));
        vec3 world = mix(vec3(0.1, 0.3, 0.6), vec3(0.25, 0.45, 0.2), smoothstep(0.45, 0.55, terrain));
        world = mix(world, vec3(0.9), smoothstep(0.62, 0.7, fbm(vec2(along * 70.0 - uTime * 0.05, v * 5.0))) * 0.7);
        vec3 ringCol = mix(hull, world * 0.9, land);

        // Running lights that pulse with the beat.
        float lights = step(0.93, hash12(cell + 7.0)) * step(0.8, abs(v)) * (0.5 + 0.5 * sin(uTime * 3.0 + hash12(cell) * 20.0));
        ringCol += vec3(1.0, 0.7, 0.3) * lights * (1.0 + uBeat * 2.0);

        float edge = smoothstep(width, width * 0.9, inner);
        col = mix(col, ringCol, edge);
    }
    col += vec3(0.4, 0.55, 0.9) * exp(-abs(inner - width) * 120.0) * 0.25 * (1.0 + uLevel);
    return col;
}
