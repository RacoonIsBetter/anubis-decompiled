#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;

out vec3 skyDirection;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    skyDirection = Position;
}
