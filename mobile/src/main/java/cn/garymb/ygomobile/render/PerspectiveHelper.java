package cn.garymb.ygomobile.render;

import android.graphics.Camera;
import android.graphics.Matrix;

public class PerspectiveHelper {

    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    private float rotateX = 0f;
    private float rotateY = 0f;
    private float rotateZ = 0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private float translateZ = 0f;

    public void reset() {
        rotateX = rotateY = rotateZ = 0f;
        translateX = translateY = translateZ = 0f;
 }

    public void setRotation(float x, float y, float z) {
        rotateX = x;
        rotateY = y;
        rotateZ = z;
    }

    public void setTranslation(float x, float y, float z) {
        translateX = x;
        translateY = y;
        translateZ = z;
    }

    public Matrix apply(float pivotX, float pivotY) {
        matrix.reset();
        camera.save();
        camera.rotateX(rotateX);
        camera.rotateY(rotateY);
        camera.rotateZ(rotateZ);
        camera.translate(translateX, translateY, translateZ);
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-pivotX, -pivotY);
        matrix.postTranslate(pivotX, pivotY);
        return matrix;
    }

    public static Matrix createPerspectiveMatrix(float centerX, float centerY, float rotX, float rotZ, float zOffset) {
        Matrix m = new Matrix();
        Camera cam = new Camera();
        cam.save();
        cam.translate(0, 0, zOffset);
        cam.rotateX(rotX);
        cam.rotateZ(rotZ);
        cam.getMatrix(m);
        cam.restore();
        m.preTranslate(-centerX, -centerY);
        m.postTranslate(centerX, centerY);
        return m;
    }

    public static Matrix createCard3DMatrix(float cx, float cy, float rotY, float zElevation) {
        Matrix m = new Matrix();
        Camera cam = new Camera();
        cam.save();
        cam.translate(0, 0, zElevation);
        cam.rotateY(rotY);
        cam.getMatrix(m);
        cam.restore();
        m.preTranslate(-cx, -cy);
        m.postTranslate(cx, cy);
        return m;
    }

    public static float[] projectPoint3D(float x, float y, float z, float rotXDeg, float centerX, float centerY) {
        float radX = (float) Math.toRadians(rotXDeg);
        float cosX = (float) Math.cos(radX);
        float sinX = (float) Math.sin(radX);
        float projY = (y - centerY) * cosX - z * sinX + centerY;
        float projZ = (y - centerY) * sinX + z * cosX;
        float scale = 1.0f / (1.0f + projZ * 0.001f);
        float projX = (x - centerX) * scale + centerX;
        projY = (projY - centerY) * scale + centerY;
        return new float[]{projX, projY, scale};
    }
}