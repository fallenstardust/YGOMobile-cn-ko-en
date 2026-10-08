package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.Display;
import android.view.WindowManager;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

import cn.garymb.ygomobile.game.GameField;

/**
 * 相机解算与视锥/投影矩阵（由 GameFieldView 的「相机参数 / 取景内容真值 / 矩阵与相机缓存」分栏拆出）。
 * <p>
 * 全部相机姿态由「俯仰角 + GameFieldView 实际宽高」+ {@link GameField} 几何动态解算，随屏幕
 * 尺寸/旋转/折叠自适应重建。运行期 GL 与设计时 Canvas 预览共用同一套 {@link #solveCamera(int, int)}，
 * 保证布局编辑器所见即所得。mVP/mCamRot/pickInvVP 等相机产物集中于此，供门面底层绘制原语、
 * 触摸拾取射线反投影与设计时预览读取（同包包级私有直连）。
 */
final class FieldCamera {

    private final GameFieldView view;

    // === 相机参数（俯仰角可由设置调整，默认俯视 52°：比 60° 更平，配合顶部内缩避免对方手卡顶到 gameTopInfo）===
    // 视点到注视点距离：与俯仰角共同决定相机位置（越高越俯视）
    private static final float CAMERA_DISTANCE = 7.6f;
    static final float CAM_X = 3.95f;
    private static final float CAM_LOOK_Y = 0.3f;

    // === 取景内容真值（全部由 GameField 几何推导，随俯仰角/屏幕宽高动态解算，不再硬编码锚点）===
    /** 手卡 billboard 所在平面高度（gframe getCardLocation：LOCATION_HAND z=0.5） */
    static final float HAND_Z = 0.5f;
    /** 我方 / 对方手卡行的场地 y（gframe：4.0 / -3.4；对方行随 GameFieldGeometry 的
     *  OPP_HAND_PUSH=0.35 整体外推至 -3.75，减少遮挡魔陷区） */
    static final float SELF_HAND_Y = 4.0f;
    static final float OPP_HAND_Y = -3.75f;
    /** 我方近端魔陷区外缘 y：由几何真值派生 = szoneCY(0,<5) 中心 + 半格高，
     *  随 ZONE_GAP 统一间隙自动跟随（原硬编码 3.2，现 ≈3.24），手卡屏幕上缘不得越过此线 */
    private static final float SZONE_NEAR_Y = szoneNearY();

    private static float szoneNearY() {
        float[] r = GameField.getZoneRect(0, 0x08, 0);
        return r == null ? 3.2f : r[1] + r[3] / 2f;
    }
    /** 手卡屏幕上缘与魔陷区外缘之间保留的间隙（沿视线投影到地面后度量） */
    private static final float HAND_CLEAR_GAP = 0.06f;
    /** 纵向取景锚点相对手卡极值的安全裕量倍率 */
    private static final float ANCHOR_FOV_MARGIN = 1.04f;
    /** 可交互内容外侧安全边距（世界单位） */
    private static final float CONTENT_PAD = 0.12f;
    /** 视点距离夹取：过近透视畸变过大，过远画面变平且深度精度下降 */
    private static final float MIN_CAM_D = 5.0f;
    private static final float MAX_CAM_D = 14.0f;
    /** 近/远裁剪面：由 0.5/100 收紧到 1/60，同等深度位宽下精度提升约 20 倍 */
    private static final float CAM_NEAR = 1.0f;
    private static final float CAM_FAR = 60.0f;
    /** 竖屏（h>w）专用仰角：85° 近俯视（用户规格，保留轻微透视又使左侧我方额外卡组不出屏），
     *  横屏恢复设置仰角 */
    private static final float PORTRAIT_ELEVATION = 85f;

    /**
     * 需要完整入镜的可交互内容（怪兽区 / 魔陷区 / 堆叠区）→ {横向半宽(相对场地中轴), 所在 y 行}。
     * 底板贴图的空白边距不计入：横向取景只保证“能点到的东西”不被裁掉，
     * 从而在同样的屏幕宽高比下让卡片尽可能大（底板近端两角允许略微出画）。
     */
    private static final float[][] CONTENT_RECTS = buildContentRects();

    private static float[][] buildContentRects() {
        float[][] tmp = new float[64][];
        int[] piles = {0x01, 0x10, 0x20, 0x40};
        int n = 0;
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < GameField.MAX_MONSTER_ZONE && n < tmp.length; i++)
                n = addContentRect(tmp, n, GameField.getZoneRect(p, 0x04, i));
            for (int i = 0; i < GameField.MAX_SPELL_ZONE && n < tmp.length; i++)
                n = addContentRect(tmp, n, GameField.getZoneRect(p, 0x08, i));
            for (int loc : piles) {
                if (n >= tmp.length) break;
                n = addContentRect(tmp, n, GameField.getPileRect(p, loc));
            }
        }
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) out[i] = tmp[i];
        return out;
    }

    private static int addContentRect(float[][] dst, int idx, float[] rect) {
        if (rect == null) return idx;
        float half = rect[2] * 0.5f;
        float hw = Math.max(Math.abs(rect[0] + half - CAM_X), Math.abs(rect[0] - half - CAM_X)) + CONTENT_PAD;
        dst[idx] = new float[]{hw, rect[1]};
        return idx + 1;
    }

    /**
     * 竖屏取景锚点（用户规格：「以我方的额外卡组格子左下角和卡组格子的右下角贴住屏幕边缘」
     * 且格子水平居中）：精确取我方两个堆叠列（额外 0x40 / 卡组 0x01）的近端下角对
     * （无 CONTENT_PAD、不含外侧灵摆/场地区纵列——允许被视锥裁切），视锥横向中心取两角
     * 中点而非 CAM_X，两侧同时贴住屏幕左右缘且内容不左偏；半宽收窄到两角间距之半，
     * 等效把场地/场上卡片/手卡整体拉近变大。
     * @return {近端底缘 y, 横向半宽, 横向中心 x}；几何未就绪返回 null
     */
    private static float[] portraitNearCorners() {
        float[] extra = GameField.getPileRect(0, 0x40);   // 我方左侧：额外卡组格子
        float[] deck = GameField.getPileRect(0, 0x01);    // 我方右侧：卡组格子
        if (extra == null || deck == null) return null;
        float leftX = extra[0] - extra[2] * 0.5f;         // 额外格子左下角 x
        float rightX = deck[0] + deck[2] * 0.5f;          // 卡组格子右下角 x
        float nearY = Math.max(extra[1] + extra[3] * 0.5f, deck[1] + deck[3] * 0.5f);
        float midX = (leftX + rightX) * 0.5f;
        float halfW = Math.max(midX - leftX, rightX - midX);
        if (halfW <= 0.1f) return null;
        return new float[]{nearY, halfW, midX};
    }

    // === 矩阵与相机缓存 ===
    final float[] mProj = new float[16];
    final float[] mView = new float[16];
    final float[] mVP = new float[16];
    // 相机姿态矩阵（view 旋转部分的转置）：手卡 billboard 平行屏幕用
    final float[] mCamRot = new float[16];
    private final float[] pickInvVP = new float[16];
    final Object camLock = new Object();
    // 视点位置：drawCard 判定卡片正/背面朝向用（仅 GL 线程读写）
    float camEyeX = CAM_X, camEyeY = 0f, camEyeZ = 1f;
    // 我方手卡行动态后移量：solveCamera 写入，绘制与触摸命中共用（主线程也会读）
    volatile float selfHandShift = 0f;

    FieldCamera(GameFieldView view) {
        this.view = view;
    }

    /**
     * 相机解算结果：运行期 GL 与设计时 Canvas 预览共用同一份解算，保证布局编辑器所见即所得
     */
    static final class CameraSolve {
        boolean valid;
        float eyeX = CAM_X;       // 视点横向位置（横屏恒为场地中轴 CAM_X；竖屏取内容极值中点使格子居中）
        float eyeY, eyeZ;       // 视点（eyeX 恒为 CAM_X）
        float dirY, dirZ;       // 视线前向（YZ 平面内单位向量）
        float tanV;             // tan(fovy/2)（对称基准）
        float selfHandShift;    // 我方手卡行后移量（含 XML 手动微调）
        float frustumHH;        // 离轴视锥竖向半高 tan（含顶部内缩后放大）
        float frustumC;         // 离轴视锥竖向中心偏移 tan（>0 = 视锥上移，内容整体下压）
    }

    /**
     * 由「俯仰角 + GameFieldView 实际宽高」完整解算相机，全部随屏幕尺寸动态变化：
     * <ol>
     * <li>我方手卡行 y 按俯仰角解析求解，使其屏幕上缘沿视线落到地面时恰好停在近端魔陷区外缘之外，
     * 任何俯仰角下都不会遮挡魔法陷阱区（固定后移量做不到这点）；</li>
     * <li>纵向取景锚点 = 对方手卡屏幕上缘 / 我方手卡屏幕下缘（billboard 极值，随俯仰角与手卡行变化），
     * FOV 取两锚点夹角 × 裕量；</li>
     * <li>横向按「可交互内容」逐行求 max(半宽/该行深度)，已满足即停 → 保留尽量近的视点，
     * 卡片尽可能大；宽高比过小（折叠屏展开/竖屏）后退也无法容纳时放大 FOV 兜底，任何宽高比都不丢格子。</li>
     * </ol>
     */
    CameraSolve solveCamera(int w, int h) {
        CameraSolve s = new CameraSolve();
        if (w <= 1 || h <= 1) return s;
        final boolean portrait = (float) w / h < 1f;
        // 竖屏：仰角固定 85°（几乎垂直于视角）；横屏恢复设置仰角（随旋转重建相机自然切换）
        final float cameraElevationDeg = portrait ? PORTRAIT_ELEVATION : view.cameraElevationDeg;
        final float cameraDistance = view.cameraDistance;
        final float fieldZoom = view.fieldZoom;
        final float handSelfYShift = view.handSelfYShift;
        final float topInsetPx = portrait ? 0f : view.topInsetPx;
        float aspect = (float) w / h;
        float th = (float) Math.toRadians(cameraElevationDeg);
        float cth = (float) Math.cos(th), sth = (float) Math.sin(th);
        if (sth < 1e-3f) return s;
        // 90° 正俯视：cos(90°) 浮点残差≈6e-17，钳到极小正数按正俯视参与后续解算（而非判无效回退）
        if (cth < 1e-3f) cth = 1e-3f;
        // 竖屏贴边取景锚点（几何未就绪时回退横屏同构路径）
        final float[] corners = portrait ? portraitNearCorners() : null;
        // 竖屏视锥横向中心 = 全部内容横向极值中点（映射到屏幕中心），横屏维持 CAM_X
        s.eyeX = corners != null ? corners[2] : CAM_X;
        // 手卡平行屏幕，相机 up≈(0,-sinθ,cosθ)：卡片半高在世界 Y/Z 上的投影
        float hY = FieldGeometry.CARD_H * 0.5f * sth;
        float hZ = FieldGeometry.CARD_H * 0.5f * cth;
        float topZ = HAND_Z + hZ;      // 手卡屏幕上缘的世界 z
        float bottomZ = HAND_Z - hZ;   // 手卡屏幕下缘的世界 z

        float D = Math.max(MIN_CAM_D, Math.min(MAX_CAM_D, cameraDistance));
        float eyeY = CAM_LOOK_Y + cth * D, eyeZ = sth * D;
        float dY = 0f, dZ = -1f, tanV = 0.5f, selfCy = SELF_HAND_Y;
        for (int i = 0; i < 8; i++) {
            eyeY = CAM_LOOK_Y + cth * D;
            eyeZ = sth * D;
            selfCy = solveSelfHandY(eyeY, eyeZ, hY, topZ);
            float v1y = (OPP_HAND_Y - hY) - eyeY, v1z = topZ - eyeZ;
            float v2y, v2z;
            if (corners != null) {
                // 竖屏下锚点不用我方手卡下缘，改为我方场地近端底缘线（额外/卡组下角所在行，z=0）
                v2y = corners[0] - eyeY;
                v2z = 0f - eyeZ;
            } else {
                v2y = (selfCy + hY) - eyeY;
                v2z = bottomZ - eyeZ;
            }
            float l1 = (float) Math.sqrt(v1y * v1y + v1z * v1z);
            float l2 = (float) Math.sqrt(v2y * v2y + v2z * v2z);
            if (l1 < 1e-4f || l2 < 1e-4f) break;
            float d1y = v1y / l1, d1z = v1z / l1, d2y = v2y / l2, d2z = v2z / l2;
            float dot = Math.max(-1f, Math.min(1f, d1y * d2y + d1z * d2z));
            tanV = (float) Math.tan(Math.acos(dot) * 0.5 * ANCHOR_FOV_MARGIN);
            float by = d1y + d2y, bz = d1z + d2z;
            float bl = (float) Math.sqrt(by * by + bz * bz);
            if (bl < 1e-4f) break;
            dY = by / bl;
            dZ = bz / bl;
            s.valid = true;
            // 竖屏（corners 贴边取景路径）：视点绝不退让——退让循环在竖屏纵横比下恒不满足，
            // 会把 D 一路推到 MAX_CAM_D 拉平透视、卡片整体缩小（用户反馈「缩得太小」根因）。
            // 取景尺寸由下方横向贴边公式唯一决定，不再纵向兜底放大。
            if (corners != null) break;
            float needH = contentHalfTan(eyeY, eyeZ, dY, dZ);
            if (needH * fieldZoom <= tanV * aspect) break;
            if (D >= MAX_CAM_D - 1e-3f) break;
            D = Math.min(MAX_CAM_D, D * 1.35f);
        }
        if (!s.valid) return s;

        float hh, c;
        if (corners != null) {
            // 竖屏「居中+贴左右缘」取景（用户真机规格：我方左侧额外卡组格子左下角、
            // 右侧卡组格子右下角分别落在 layout_game_right 左/右边缘上，卡片尽量大）：
            // 横向半宽 tan 由近端两角精确决定（fRight=hh·aspect·near），x 映射与离轴量 c
            // 无关，故取 c=0 使视锥关于视轴（两锚点角平分线）对称：内容上下留白均行、
            // 决斗场整体居中，近端两角精确落在屏幕左右缘。
            // 不再做 hh≥tanV 纵向兜底——85° 近俯视下纵向锚点 tanV 远大于横向贴边所需，
            // 兜底会把视野抬回纵向容纳、卡片缩得与修改前一样小（真机反馈根因）；
            // 以横向贴边为唯一基准，纵向超出视锥的部分按 c=0 均分裁切，用户规格优先。
            float depthCorner = Math.max(0.2f, (corners[0] - eyeY) * dY - eyeZ * dZ);
            hh = corners[1] * fieldZoom / depthCorner / aspect;
            c = 0f;
        } else {
            float need = contentHalfTan(eyeY, eyeZ, dY, dZ) * fieldZoom;
            if (need / aspect > tanV) tanV = need / aspect;
            if (tanV > 1.6f) tanV = 1.6f;

            // 顶部内缩（问题1）：用离轴视锥把「对方手卡上缘」锚到屏幕顶部内缩线 ndcTop 之下，
            // 我方手卡下缘仍锚到 ndc=-1（屏幕底），从而在不裁掉任何一方的前提下为 gameTopInfo 让出顶部空间。
            // 由对称半角 tanV 解离轴参数：hh = 2·tanV/(1+ndcTop)，c = hh - tanV（推导见类注释）。
            float inset = Math.max(0f, Math.min(topInsetPx, h * 0.45f));
            float ndcTop = (h > 1f) ? (1f - 2f * inset / h) : 1f;
            if (ndcTop < 0.05f) ndcTop = 0.05f;
            hh = 2f * tanV / (1f + ndcTop);
            c = hh - tanV;
        }
        s.frustumHH = hh;
        s.frustumC = c;

        float shift = SELF_HAND_Y - selfCy + handSelfYShift;
        s.eyeY = eyeY;
        s.eyeZ = eyeZ;
        s.dirY = dY;
        s.dirZ = dZ;
        s.tanV = tanV;
        s.selfHandShift = Math.max(-2.5f, Math.min(2.5f, shift));
        return s;
    }

    /**
     * 解算我方手卡行的场地 y：手卡是 billboard，其屏幕上缘 (cy-hY, HAND_Z+hZ) 沿视线落到地面(z=0)
     * 的交点必须落在近端魔陷区外缘之外 HAND_CLEAR_GAP。视线越斜（俯仰角越小）所需后移量越大，
     * 因此必须随相机动态求解，不能用固定常量。
     */
    private static float solveSelfHandY(float eyeY, float eyeZ, float hY, float topZ) {
        if (eyeZ < 1e-3f) return SELF_HAND_Y;
        float dz = eyeZ - topZ;
        if (dz < 0.2f) return SELF_HAND_Y + 2.0f;
        float cy = eyeY + hY - (eyeY - SZONE_NEAR_Y - HAND_CLEAR_GAP) * dz / eyeZ;
        return Math.max(3.4f, Math.min(6.5f, cy));
    }

    /**
     * 所有可交互内容中 max(半宽 / 该行沿视线深度)，即容纳全部内容所需的 tan(fovx/2)
     */
    private static float contentHalfTan(float eyeY, float eyeZ, float dY, float dZ) {
        float need = 0f;
        for (int i = 0; i < CONTENT_RECTS.length; i++) {
            float[] r = CONTENT_RECTS[i];
            float depth = (r[1] - eyeY) * dY + (0f - eyeZ) * dZ;
            if (depth < 0.2f) continue;
            float t = r[0] / depth;
            if (t > need) need = t;
        }
        return need;
    }

    void updateCamera() {
        final int viewW = view.viewW;
        final int viewH = view.viewH;
        CameraSolve s = solveCamera(viewW, viewH);
        if (!s.valid) return;
        float aspect = (float) viewW / viewH;

        camEyeX = s.eyeX;
        camEyeY = s.eyeY;
        camEyeZ = s.eyeZ;
        selfHandShift = s.selfHandShift;

        // 离轴（顶部内缩）透视视锥：bottom/top 非对称由 frustumHH 承担（含顶部内缩放大）。
        // 横向半宽必须与竖向同一基准 frustumHH（而非 tanV），保证横竖缩放因子一致——
        // 否则 frustumHH(=2tanV/(1+ndcTop))>tanV 会把卡片矩形横向拉宽（比例失真）。用 frustumHH*aspect
        // 使场地位于等距（isotropic）视锥内，卡片严格保持 177:254 原始比例；GameFieldView 宽度仍由
        // layout_game_right 决定（match_parent），横向自然留白属正常取景，不再以拉伸换取贴边。
        float near = CAM_NEAR;
        float fTop = (s.frustumC + s.frustumHH) * near;
        float fBottom = (s.frustumC - s.frustumHH) * near;
        float fRight = s.frustumHH * aspect * near;
        Matrix.frustumM(mProj, 0, -fRight, fRight, fBottom, fTop, near, CAM_FAR);
        Matrix.setLookAtM(mView, 0, s.eyeX, s.eyeY, s.eyeZ,
                s.eyeX, s.eyeY + s.dirY, s.eyeZ + s.dirZ, 0f, 0f, 1f);
        Matrix.multiplyMM(mVP, 0, mProj, 0, mView, 0);

        synchronized (camLock) {
            Matrix.invertM(pickInvVP, 0, mVP, 0);
        }

        // 手卡 billboard 姿态：view 旋转部分的转置（列=相机 right/up/backward 轴）
        mCamRot[0] = mView[0];
        mCamRot[1] = mView[4];
        mCamRot[2] = mView[8];
        mCamRot[3] = 0f;
        mCamRot[4] = mView[1];
        mCamRot[5] = mView[5];
        mCamRot[6] = mView[9];
        mCamRot[7] = 0f;
        mCamRot[8] = mView[2];
        mCamRot[9] = mView[6];
        mCamRot[10] = mView[10];
        mCamRot[11] = 0f;
        mCamRot[12] = 0f;
        mCamRot[13] = 0f;
        mCamRot[14] = 0f;
        mCamRot[15] = 1f;

        // 相机重建完成：通知覆盖层重新定位阶段按钮（post 到主线程执行）
        Runnable camListener = view.onCameraChangedListener;
        if (camListener != null) view.post(camListener);
    }

    /**
     * 世界坐标（绘制空间：x 需为镜像后坐标，与 drawCard/drawZoneSlots 传入 mVP 前一致）
     * 投影到屏幕像素坐标；相机未就绪返回 null。camLock 保护，任意线程可调用
     */
    float[] projectWorldPoint(float x, float y, float z) {
        float[] vp = new float[16];
        int w, h;
        synchronized (camLock) {
            System.arraycopy(mVP, 0, vp, 0, 16);
            w = view.viewW;
            h = view.viewH;
        }
        if (w <= 1 || h <= 1) return null;
        float[] v = {x, y, z, 1f};
        float[] o = new float[4];
        Matrix.multiplyMV(o, 0, vp, 0, v, 0);
        if (Math.abs(o[3]) < 1e-6f) return null;
        float nx = o[0] / o[3], ny = o[1] / o[3];
        return new float[]{(nx + 1f) * 0.5f * w, (1f - ny) * 0.5f * h};
    }

    /**
     * 拾取用视逆投影矩阵快照（camLock 下拷贝，供射线反投影）
     */
    float[] pickInvVPSnapshot() {
        float[] inv = new float[16];
        synchronized (camLock) {
            System.arraycopy(pickInvVP, 0, inv, 0, 16);
        }
        return inv;
    }

    /**
     * 对方手卡屏幕上缘的屏幕 y（像素，相对视图左上角）：供覆盖层把聊天信息与中央提示文本
     * 锚定在「对方手卡正上方」。相机未就绪时回退为当前顶部内缩量。
     */
    float computeOpponentHandTopScreenY() {
        float th = (float) Math.toRadians(view.cameraElevationDeg);
        float hY = FieldGeometry.CARD_H * 0.5f * (float) Math.sin(th);
        float hZ = FieldGeometry.CARD_H * 0.5f * (float) Math.cos(th);
        float[] s = projectWorldPoint(CAM_X, OPP_HAND_Y - hY, HAND_Z + hZ);
        return s != null ? s[1] : view.topInsetPx;
    }

    /**
     * 请求屏幕最高刷新率显示模式（高刷屏跑满 90/120/144Hz 的前提）：与 {@link DepthConfigChooser}
     * 同属 GL 渲染 surface 的显示配置，集中于此供门面生命周期转发调用。
     */
    static void applyHighRefreshRate(GameFieldView view) {
        try {
            Context ctx = view.getContext();
            if (!(ctx instanceof Activity)) return;
            Activity act = (Activity) ctx;
            Display display = act.getWindowManager().getDefaultDisplay();
            Display.Mode[] modes = display.getSupportedModes();
            Display.Mode best = null;
            for (Display.Mode m : modes) {
                if (best == null || m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            if (best == null) return;
            WindowManager.LayoutParams lp = act.getWindow().getAttributes();
            if (lp.preferredDisplayModeId != best.getModeId()) {
                lp.preferredDisplayModeId = best.getModeId();
                act.getWindow().setAttributes(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * EGL 配置：优先 RGBA8888 + 24bit 深度，个别设备无该配置时按 24→16→0 逐级回落，避免直接黑屏
     */
    static final class DepthConfigChooser implements GLSurfaceView.EGLConfigChooser {
        private static final int[] DEPTH_CANDIDATES = {24, 16, 0};

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] num = new int[1];
            int[] got = new int[1];
            for (int depth : DEPTH_CANDIDATES) {
                int[] attrs = {
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_DEPTH_SIZE, depth,
                        EGL10.EGL_STENCIL_SIZE, 0,
                        EGL10.EGL_NONE
                };
                if (!egl.eglChooseConfig(display, attrs, null, 0, num) || num[0] <= 0) continue;
                EGLConfig[] configs = new EGLConfig[num[0]];
                if (!egl.eglChooseConfig(display, attrs, configs, configs.length, num)) continue;
                for (EGLConfig config : configs) {
                    if (config == null) continue;
                    if (egl.eglGetConfigAttrib(display, config, EGL10.EGL_DEPTH_SIZE, got) && got[0] >= depth) {
                        return config;
                    }
                }
            }
            throw new IllegalArgumentException("No RGBA8888 EGL config available");
        }
    }
}
