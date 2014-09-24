#version 110

varying vec2 vTexCoords;

uniform sampler2DMS texColor;

void main(void) {
	ivec2 s = textureSize(texColor);
	ivec2 p = ivec2(round(vTexCoords.x * (s.x-1)), round(vTexCoords.y * (s.y-1)));
//	ivec2 p = ivec2(gl_FragCoord.xy)	// efficient, but works only for fullscreen

	vec4 s0 = texelFetch(texColor, p, 0);
	vec4 s1 = texelFetch(texColor, p, 1);

	gl_FragColor = mix(s0, s1, 0.5);
}