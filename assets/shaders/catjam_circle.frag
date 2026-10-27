#version 300 es
precision highp float;

in vec2 vUV;
in vec4 vColor;

uniform sampler2D uTexture;

// UVs of the current frame within the sheet: u0, v0, u1, v1.
uniform vec4 uFrameRect;

// Clip radius in frame space, 0.5 touches the frame edges.
uniform float uRadius;

out vec4 fragColor;

void main() {
    // highp: a frame spans only a small part of the atlas UVs, mediump would shift the circle.
    vec2 local = (vUV - uFrameRect.xy) / (uFrameRect.zw - uFrameRect.xy);
    float dist = distance(local, vec2(0.5));

    // A small smooth edge avoids aliasing at the circle rim.
    float clip = 1.0 - smoothstep(uRadius - 0.02, uRadius, dist);
    if (clip <= 0.0) discard;

    fragColor = texture(uTexture, vUV) * vColor * vec4(1.0, 1.0, 1.0, clip);
}
