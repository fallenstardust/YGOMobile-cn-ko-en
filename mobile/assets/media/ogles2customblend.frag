precision mediump float;

uniform bool uTextureUsage0;

varying vec2 varTexCoord0;

varying vec4 varVertexColor;

uniform sampler2D uTextureUnit0;

void main (void)
{
    vec4 Color = varVertexColor;

    if(uTextureUsage0)
        Color *= texture2D(uTextureUnit0, varTexCoord0);
        
    Color.a = 1.0;
        
    gl_FragColor =  Color;
}