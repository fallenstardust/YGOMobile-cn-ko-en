precision mediump float;

uniform bool uTextureUsage0;

varying vec2 varTexCoord0;

uniform sampler2D uTextureUnit0;

void main (void)
{
    vec4 Color = vec4(1.0, 1.0, 1.0, 1.0);

    if(uTextureUsage0)
        Color *= texture2D(uTextureUnit0, varTexCoord0);

    gl_FragColor =  Color;
}