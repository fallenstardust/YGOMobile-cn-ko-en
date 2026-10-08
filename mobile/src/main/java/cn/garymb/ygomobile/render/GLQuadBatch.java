package cn.garymb.ygomobile.render;

import android.opengl.GLES30;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 底层四边形绘制原语（自 GameFieldView 平移，逻辑零改）：单位矩形 VAO + 纹理/纯色两个着色器程序，
 * 世界透视与屏幕正交两套投影入口，供同包各 renderer 经 view.quad 复用。
 * 矩阵 scratch（view.mModel/mMVP/mOrthoVP）与相机 mVP 仍归视图共享状态，经反向引用直连。
 */
class GLQuadBatch {

    // === 着色器（ES 3.0 / GLSL 300 es）：纹理贴图（含 UV 翻转子矩形）与纯色 ===
    private static final String VS =
            "#version 300 es\n" +
                    "layout(location=0) in vec2 aPos;\n" +
                    "layout(location=1) in vec2 aUV;\n" +
                    "uniform mat4 uMVP;\n" +
                    "uniform float uFlipU;\n" +
                    "uniform float uFlipV;\n" +
                    "uniform vec4 uUVRect;\n" +
                    "out vec2 vUV;\n" +
                    "void main(){ vec2 uv=vec2(mix(aUV.x,1.0-aUV.x,uFlipU),mix(aUV.y,1.0-aUV.y,uFlipV)); vUV=uUVRect.xy+uv*uUVRect.zw; gl_Position=uMVP*vec4(aPos,0.0,1.0); }\n";

    private static final String FS_TEX =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "in vec2 vUV;\n" +
                    "uniform sampler2D uTex;\n" +
                    "uniform vec4 uTint;\n" +
                    "out vec4 fragColor;\n" +
                    "void main(){ fragColor=texture(uTex,vUV)*uTint; }\n";

    private static final String FS_COLOR =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "uniform vec4 uColor;\n" +
                    "out vec4 fragColor;\n" +
                    "void main(){ fragColor=uColor; }\n";

    // 单位矩形（XY 平面，法线 +Z；v=0 在局部 -Y 边即 Bitmap 顶部；u 为标准布局。
    // 场地卡 uFlipU=1 抵消相机 +X→屏幕左 镜像；手卡 billboard 局部+Y=屏幕上方，
    // 需 uFlipU=0 + uFlipV=1 恢复正向贴图）
    private static final float[] QUAD = {
            -0.5f, -0.5f, 0f, 0f,
            0.5f, -0.5f, 1f, 0f,
            -0.5f, 0.5f, 0f, 1f,
            0.5f, 0.5f, 1f, 1f,
    };

    // === GL 资源（着色器程序句柄 / uniform 位置 / 单位矩形 VAO，仅本类绘制原语使用）===
    private int texProg, colorProg;
    private int texLocMVP, texLocTint, texLocTex, texLocFlipU, texLocFlipV, texLocUVRect;
    private int colorLocMVP, colorLocColor;
    private int vao;

    private final GameFieldView view;

    GLQuadBatch(GameFieldView view) {
        this.view = view;
    }

    /** onSurfaceCreated 回调：编译两个程序、查询 uniform 位置、建立单位矩形 VAO */
    void init() {
        texProg = createProgram(VS, FS_TEX);
        colorProg = createProgram(VS, FS_COLOR);
        texLocMVP = GLES30.glGetUniformLocation(texProg, "uMVP");
        texLocTint = GLES30.glGetUniformLocation(texProg, "uTint");
        texLocTex = GLES30.glGetUniformLocation(texProg, "uTex");
        texLocFlipU = GLES30.glGetUniformLocation(texProg, "uFlipU");
        texLocFlipV = GLES30.glGetUniformLocation(texProg, "uFlipV");
        texLocUVRect = GLES30.glGetUniformLocation(texProg, "uUVRect");
        colorLocMVP = GLES30.glGetUniformLocation(colorProg, "uMVP");
        colorLocColor = GLES30.glGetUniformLocation(colorProg, "uColor");

        FloatBuffer fb = ByteBuffer.allocateDirect(QUAD.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(QUAD).position(0);
        int[] vaos = new int[1], vbos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        vao = vaos[0];
        GLES30.glGenBuffers(1, vbos, 0);
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fb.capacity() * 4, fb, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8);
        GLES30.glBindVertexArray(0);
    }

    void drawFlatQuad(float cx, float cy, float z, float w, float h,
                      float r, float g, float b, float a) {
        Matrix.setIdentityM(view.mModel, 0);
        Matrix.translateM(view.mModel, 0, cx, cy, z);
        Matrix.scaleM(view.mModel, 0, w, h, 1f);
        drawQuadColor(view.mModel, r, g, b, a);
    }

    void drawQuadTex(float[] model, int texId, float alpha) {
        drawQuadTex(model, texId, alpha, 1f, 0f);
    }

    void drawQuadTex(float[] model, int texId, float alpha, float flipU, float flipV) {
        drawQuadTexUV(model, texId, alpha, flipU, flipV, 0f, 0f, 1f, 1f);
    }

    void drawQuadTexUV(float[] model, int texId, float alpha, float flipU, float flipV,
                       float offU, float offV, float scU, float scV) {
        if (texId <= 0) return;
        GLES30.glUseProgram(texProg);
        Matrix.multiplyMM(view.mMVP, 0, view.cam.mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(texLocMVP, 1, false, view.mMVP, 0);
        GLES30.glUniform4f(texLocTint, 1f, 1f, 1f, alpha);
        GLES30.glUniform1f(texLocFlipU, flipU);
        GLES30.glUniform1f(texLocFlipV, flipV);
        GLES30.glUniform4f(texLocUVRect, offU, offV, scU, scV);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
        GLES30.glUniform1i(texLocTex, 0);
        glBindQuadVao();
    }

    private void glBindQuadVao() {
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glBindVertexArray(0);
    }

    void drawQuadColor(float[] model, float r, float g, float b, float a) {
        GLES30.glUseProgram(colorProg);
        Matrix.multiplyMM(view.mMVP, 0, view.cam.mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(colorLocMVP, 1, false, view.mMVP, 0);
        GLES30.glUniform4f(colorLocColor, r, g, b, a);
        glBindQuadVao();
    }

    /** 屏幕像素正交空间绘纯色四边形（供 HUD/覆盖层复用） */
    void drawScreenQuadColor(float cx, float cy, float w, float h,
                             float r, float g, float b, float a) {
        GLES30.glUseProgram(colorProg);
        Matrix.setIdentityM(view.mModel, 0);
        Matrix.translateM(view.mModel, 0, cx, cy, 0f);
        Matrix.scaleM(view.mModel, 0, w, h, 1f);
        Matrix.multiplyMM(view.mMVP, 0, view.mOrthoVP, 0, view.mModel, 0);
        GLES30.glUniformMatrix4fv(colorLocMVP, 1, false, view.mMVP, 0);
        GLES30.glUniform4f(colorLocColor, r, g, b, a);
        glBindQuadVao();
    }

    /** 屏幕像素正交空间绘贴图四边形（阶段按钮标签 / HUD 数字，供各 renderer 复用） */
    void drawScreenQuadTex(float cx, float cy, float w, float h, int texId, float alpha) {
        if (texId <= 0) return;
        GLES30.glUseProgram(texProg);
        Matrix.setIdentityM(view.mModel, 0);
        Matrix.translateM(view.mModel, 0, cx, cy, 0f);
        Matrix.scaleM(view.mModel, 0, w, h, 1f);
        Matrix.multiplyMM(view.mMVP, 0, view.mOrthoVP, 0, view.mModel, 0);
        GLES30.glUniformMatrix4fv(texLocMVP, 1, false, view.mMVP, 0);
        GLES30.glUniform4f(texLocTint, 1f, 1f, 1f, alpha);
        GLES30.glUniform1f(texLocFlipU, 0f);
        GLES30.glUniform1f(texLocFlipV, 0f);
        GLES30.glUniform4f(texLocUVRect, 0f, 0f, 1f, 1f);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
        GLES30.glUniform1i(texLocTex, 0);
        glBindQuadVao();
    }

    // ==================== 着色器工具 ====================

    private static int loadShader(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] st = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, st, 0);
        if (st[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }

    /** 供 CardOverlayRenderer 构建攻击弧 3D 逐顶点色程序（同包包级私有） */
    static int createProgram(String vs, String fs) {
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, loadShader(GLES30.GL_VERTEX_SHADER, vs));
        GLES30.glAttachShader(p, loadShader(GLES30.GL_FRAGMENT_SHADER, fs));
        GLES30.glLinkProgram(p);
        int[] st = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, st, 0);
        if (st[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(p);
            GLES30.glDeleteProgram(p);
            throw new RuntimeException("Program link failed: " + log);
        }
        return p;
    }
}
