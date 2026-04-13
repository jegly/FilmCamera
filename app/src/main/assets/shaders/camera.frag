#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uCameraTexture;
uniform sampler2D          uLutTexture;
uniform float              uLutIntensity;
uniform float              uGrainAmount;
uniform float              uGrainSize;
uniform float              uTime;
uniform int                uLutSize;
uniform int                uLutLayout;
uniform int                uImgWidth;
uniform vec2               uResolution;

varying vec2 vTexCoord;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

float grain(vec2 uv) {
    vec2 cell = floor(uv * uResolution / uGrainSize);
    float n = rand(cell + fract(uTime * 0.1));
    return (n - 0.5) * 2.0;
}

vec3 sampleLut(float ri, float gi, float bi) {
    float size  = float(uLutSize);
    if (uLutLayout == 0) {
        float size2 = size * size;
        float u = (ri + gi * size + 0.5) / size2;
        float v = (bi + 0.5) / size;
        return texture2D(uLutTexture, vec2(u, v)).rgb;
    } else {
        float imgW  = float(uImgWidth);
        float flat  = ri + gi * size + bi * size * size;
        float u = (mod(flat, imgW) + 0.5) / imgW;
        float v = (floor(flat / imgW) + 0.5) / imgW;
        return texture2D(uLutTexture, vec2(u, v)).rgb;
    }
}

vec3 applyLut(vec3 color) {
    float size = float(uLutSize);
    color = clamp(color, 0.0, 1.0);
    vec3 lutCoord = color * (size - 1.0);
    float ri = min(floor(lutCoord.r), size - 2.0);
    float gi = min(floor(lutCoord.g), size - 2.0);
    float bi = min(floor(lutCoord.b), size - 2.0);
    float rf = lutCoord.r - ri;
    float gf = lutCoord.g - gi;
    float bf = lutCoord.b - bi;
    vec3 v0 = mix(
        mix(sampleLut(ri,     gi,     bi), sampleLut(ri+1.0, gi,     bi), rf),
        mix(sampleLut(ri,     gi+1.0, bi), sampleLut(ri+1.0, gi+1.0, bi), rf),
        gf);
    vec3 v1 = mix(
        mix(sampleLut(ri,     gi,     bi+1.0), sampleLut(ri+1.0, gi,     bi+1.0), rf),
        mix(sampleLut(ri,     gi+1.0, bi+1.0), sampleLut(ri+1.0, gi+1.0, bi+1.0), rf),
        gf);
    return mix(v0, v1, bf);
}

void main() {
    vec4 raw = texture2D(uCameraTexture, vTexCoord);
    vec3 color = raw.rgb;

    if (uLutIntensity > 0.001) {
        color = mix(color, applyLut(color), uLutIntensity);
    }

    if (uGrainAmount > 0.001) {
        color += vec3(grain(vTexCoord) * uGrainAmount * 0.12);
    }

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
