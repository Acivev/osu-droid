#version 300 es
precision highp float;

in vec2 vUV;
in vec4 vColor;

uniform sampler2D uTexture;

// UVs of the drawn part of the texture: u0, v0, u1, v1.
uniform vec4 uFrameRect;

// Size of the sprite and its corner radius, in pixels.
uniform vec2 uSize;
uniform float uRadius;

out vec4 fragColor;

// Signed distance to a rounded box centered at the origin, in pixels.
float roundedBoxSDF(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    // highp: the drawn part can be a small part of the texture, mediump would shift the corners.
    vec2 local = (vUV - uFrameRect.xy) / (uFrameRect.zw - uFrameRect.xy);
    vec2 p = (local - 0.5) * uSize;
    float r = min(uRadius, min(uSize.x, uSize.y) * 0.5);

    float dist = roundedBoxSDF(p, uSize * 0.5, r);
    float aa = max(fwidth(dist), 0.0001);
    float mask = clamp(0.5 - dist / aa, 0.0, 1.0);
    if (mask <= 0.0) discard;

    fragColor = texture(uTexture, vUV) * vColor * vec4(1.0, 1.0, 1.0, mask);
}
