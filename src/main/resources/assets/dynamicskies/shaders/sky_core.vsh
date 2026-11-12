#version 150

uniform mat4 uInverseViewProjection;
out vec3 vDir;

void main() {
    vec2 clip = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2.0 - 1.0;
    vec4 ray = uInverseViewProjection * vec4(clip, 0.0, 1.0);
    vDir = ray.xyz / ray.w;
    gl_Position = vec4(clip, 0.99999, 1.0);
}
