uniform mat4 uSTMatrix;
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
