#version 120

// The cube is drawn around the camera in world axes; dropping the
// translation keeps third-person / view bobbing from skewing the sky.
varying vec3 vDir;

void main() {
    vDir = gl_Vertex.xyz;
    gl_Position = gl_ProjectionMatrix * vec4(mat3(gl_ModelViewMatrix) * gl_Vertex.xyz, 1.0);
}
