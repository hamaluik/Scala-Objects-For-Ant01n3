#version 110

varying vec2 vTexCoords;

uniform sampler2DMS texColor;

void main(void) {
	ivec2 s = textureSize(texColor);
	ivec2 p = ivec2(vTexCoords.x * (s.x-1), vTexCoords.y * (s.y-1));
//	ivec2 p = ivec2(gl_FragCoord.xy)	// efficient, but works only for fullscreen

	vec4 s0 = texelFetch(texColor, p, 0);
	vec4 s1 = texelFetch(texColor, p, 1);
	vec4 s2 = texelFetch(texColor, p, 2);
	vec4 s3 = texelFetch(texColor, p, 3);

	gl_FragColor = mix(mix(s0, s1, 0.5), mix(s2, s3, 0.5), 0.5);
}