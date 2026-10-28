package cn.garymb.ygomobile.render.gamefieldview;
import cn.garymb.ygomobile.render.field.FieldGeometry;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.Display;
import android.view.WindowManager;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

import cn.garymb.ygomobile.game.gamefield.GameField;

/**
 * 相机解算与视锥/投影矩阵（由 GameFieldView 的「相机参数 / 取景内容真值 / 矩阵与相机缓存」分栏拆出）。
 * <p>
 * 全部相机姿态由「俯仰角 + GameFieldView 实际宽高」+ {@link GameField} 几何动态解算，随屏幕
 * 尺寸/旋转/折叠自适应重建。运行期 GL 与设计时 Canvas 预览共用同一套 {@link #solveCamera(int, int)}，
 * 保证布局编辑器所见即所得。mVP/mCamRot/pickInvVP 等相机产物集中于此，供门面底层绘制原语、
 * 触摸拾取射线反投影与设计时预览读取（同包包级私有直连）。
 */
public final class FieldCamera {

    private final GameFieldView view;

    // === 相机姿态基准（俯仰角 / 视点距离的实际值由 GameFieldView 按 XML/设置提供，本类不硬编默认值）===
    // 场地横向中轴与注视点 y：与 view.cameraDistance / cameraElevationDeg 共同决定视点位置。
    static final float CAM_X = 3.95f;
    private static final float CAM_LOOK_Y = 0.3f;

    // === 取景内容真值（全部由 GameField 几何推导，随俯仰角/屏幕宽高动态解算，不再硬编码锚点）===
    /** 手卡 billboard 所在平面高度（gframe getCardLocation：LOCATION_HAND z=0.5） */
    static final float HAND_Z = 0.5f;
    /** 我方 / 对方手卡行的场地 y（gframe：4.0 / -3.4；对方行随 GameFieldGeometry 的
     *  OPP_HAND_PUSH 整体外推，本值须恒等于 -3.4 − OPP_HAND_PUSH，与几何同源否则裁手卡）。
     *  注：「对方侧显得远/小」的主调参是相机距离 field_camera_distance，非此处；详见
     *  layout/layout_game_right.xml 的「决斗场微调参数总览」。 */
    static final float SELF_HAND_Y = 4.0f;
    static final float OPP_HAND_Y = -3.55f;
    /** 我方近端魔陷区外缘 y：由几何真值派生 = szoneCY(0,<5) 中心 + 半格高，
     *  还原 materials.cpp 后恰为 2.6+0.6=3.2（与 C++ 底板网格线同源），手卡屏幕上缘不得越过此线 */
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
    /** 横屏纵向净空占比参照值（两条锚线之间可用高度 / 视图全高）：取自老式 16:9 横屏实机
     *  ——其 HUD（血条行 + 提示行 + 输入框）按 dp 定值只占视图高约 26%。达到或优于该值时
     *  仰角不抬升，16:9 与低密度设备取景与旧行为完全一致（用户已满意，禁止回归）。
     *  本参照只用于「名义挤占度」换算（{@link GameFieldView#LANDSCAPE_HUD_NOMINAL_DP}），不读实测内缩 */
    private static final float LANDSCAPE_BAND_REF = 0.741f;
    /** 名义净空占比比参照值再紧 7.5 个百分点（全面屏 17~24:9 的 HUD 挤占比例）时仰角抬升到上限 */
    private static final float LANDSCAPE_BAND_SQUEEZE_FULL = 0.075f;
    /** 净空被 HUD 挤占时横屏仰角的最大抬升量（°）：俯角越陡，近/远端深度差越小，远端卡片相对
     * 越大（远/近投影比 52° 为 0.836、72° 为 0.912、平板的 85° 为 0.959）。20° 的实测标定：
     * 17:9 落在 72°、20:9 落在 68°、24:9/21:9 落在 72°，对方场卡片投影高分别为 16:9 参照
     * （7.8%视图高）的 100% / 101% / 98% / 96%（抬升前依次为 92% / 94% / 90% / 88%）；
     * 再往上抬收益递减而横向留白继增，
     *  上限同时受 {@link GameFieldView#MAX_CAMERA_ELEVATION} 钳制 */
    private static final float LANDSCAPE_ELEVATION_LIFT = 20f;

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
    public final float[] mVP = new float[16];
    // 相机姿态矩阵（view 旋转部分的转置）：手卡 billboard 平行屏幕用
    public final float[] mCamRot = new float[16];
    private final float[] pickInvVP = new float[16];
    final Object camLock = new Object();
    // 视点位置：drawCard 判定卡片正/背面朝向用（仅 GL 线程读写）
    public float camEyeX = CAM_X, camEyeY = 0f, camEyeZ = 1f;
    // 我方手卡行动态后移量：solveCamera 写入，绘制与触摸命中共用（主线程也会读）
    public volatile float selfHandShift = 0f;
    // 本次相机重建真正生效的俯仰角（横屏含净空自适应抬升）：billboard 极值反推与预览读数用
    private volatile float effElevationDeg = GameFieldView.DEFAULT_CAMERA_ELEVATION;

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
        float effectiveElevation; // 本次解算真正使用的俯仰角（横屏为净空自适应抬升后的值）
    }

    /**
     * 由「俯仰角 + GameFieldView 实际宽高」完整解算相机，全部随屏幕尺寸动态变化：
     * <ol>
     * <li>我方手卡行 y 按俯仰角解析求解，使其屏幕上缘沿视线落到地面时恰好停在近端魔陷区外缘之外，
     * 任何俯仰角下都不会遮挡魔法陷阱区（固定后移量做不到这点）；</li>
     * <li>纵向取景锚点 = 对方手卡屏幕上缘 / 我方手卡屏幕下缘（billboard 极值，随俯仰角与手卡行变化），
     * FOV 取两锚点夹角 × 裕量；</li>
     * <li>视点距离恒定于配置值（仅由俯仰角决定透视），**不再后退 D**（后退会把 D 推到上限、
     * 拉平透视使卡片整体缩小，横屏视觉上决斗场比 C++ 偏小的根因）。横屏取影视锥半高 hh
     * = max(横向贴边 hhFillW, 上下锚线恰好容纳 hhFit)：hhFillW 保证可交互内容左右不裁且随
     * GameFieldView 实际布局宽自适应，hhFit 保证双方手卡装满「血条底边→聊天输入框顶边」
     * 两条锚线之间（不裁顶也不裁底）；离轴量 c 以**下锚线**为基准，使我方手卡底缘贴聊天输入框
     * 上方、宽度主导时的纵向富余全部留在顶部（对方手卡降到提示栏下方），避免决斗场视觉偏高；</li>
     * <li>横屏仰角本身按「设备纵向 dp 高度对 HUD 名义占地的挤占程度」自适应抬升
     * （{@link #landscapeElevation}）：全面屏 17~24:9 的 HUD 为 dp 定值、占视图高比例明显高于
     * 16:9，净空被挤占时 hh 只能靠 hhFit 放大（宽填 70%，16:9 为 95%），对方场卡片跟着变小变
     * 远；抬高俯角可缩小近/远端深度差，使远端卡片投影追平 16:9，达到跨设备的统一观感。</li>
     * <li>取景尺寸的唯一自由度是两条锚线之间的净空带（姿态不变时卡片投影高≈净空带高的 12.6%，
     * 在 16:9 / 20:9 / 平板上一致）；横向只有 16:9 贴边、全面屏只填约 60%，因此全面屏卡片偏小的
     * 根治手段是回收 HUD 占地（GameTopInfoManager 的横屏等比内缩），而仰角只由 dp 高度决定、
     * 与内缩回收解耦。</li>
     * </ol>
     */
    CameraSolve solveCamera(int w, int h) {
        CameraSolve s = new CameraSolve();
        if (w <= 1 || h <= 1) return s;
        final float cameraDistance = view.cameraDistance;
        final float fieldZoom = view.fieldZoom;
        final float handSelfYShift = view.handSelfYShift;
        // 顶/底内缩横竖屏都参与解算：GameFieldView 为 setZOrderOnTop 的 GL 曲面，
        // 其上不透的底板/手卡会把顶部 HUD（含 tv_hint_message）的普通 View 整层盖住；
        // 旧竖屏把两个内缩置 0（内容居中即顶到视图上缘），是提示文本被遮挡的根因。
        // 两条锚线均为恒定量（提示栏恒占位 + 固定高、输入框恒占位，见 GameFieldController），
        // 故取景不会随提示/聊天内容显隐而重解（用户规格：决斗场不得因提示出现而改变姿态）
        final float topInsetPx = view.topInsetPx;
        final float bottomInsetPx = view.bottomInsetPx;
        final boolean portrait = (float) w / h < 1f;
        // 竖屏：仰角固定 85°（几乎垂直于视角）；横屏：以设置仰角为基准，按「设备纵向 dp 高度
        // 对名义 HUD 占地的挤占程度」自适应抬升（全面屏 17~24:9 的 dp 定值 HUD 挤占净空更多，
        // 维持 52° 会把对方场卡片压得更小更远，见 {@link #landscapeElevation}）；只读视图高与
        // 屏幕密度两个恒定量、不读实测内缩，故 HUD 为放大卡片而做的等比内缩不会反过来取消
        // 抬升（用户规格：倾斜度保持现状），同一设备同一朝向解算结果恒定 → 不随对局内容晃动
        final float cameraElevationDeg = portrait ? PORTRAIT_ELEVATION
                : landscapeElevation(view.cameraElevationDeg, h,
                        view.getResources().getDisplayMetrics().density);
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
        // 本次解算真正使用的俯仰角（横屏可能已被净空自适应抬高）：读数与 billboard 推导复用
        s.effectiveElevation = cameraElevationDeg;
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
            // 视点距离恒定于配置值（仅由俯仰角决定透视），不再后退 D：后退会把 D 推到上限
            // 拉平透视使卡片整体缩小（与竖屏同构的历史根因）。横屏取景尺寸由下方 else 分支
            // 依据「实际布局宽 + 血条下线→视图底边可用高」解算，tanV 保持为双方手卡锚点半角。
            if (corners != null) break;
        }
        if (!s.valid) return s;

        float hh, c;
        if (corners != null) {
            // 竖屏「贴左右缘 + 上下锚线」取景（用户真机规格：我方左侧额外卡组格子左下角、
            // 右侧卡组格子右下角分别落在 layout_game_right 左/右边缘上，卡片尽量大）：横向半宽 tan
            // 由近端两角精确决定（fRight=hh·aspect·near），x 映射与离轴量 c 无关，故 hh 保持不变即
            // 左右贴边规格不受影响；竖向则解离轴量 c，使底板远缘与对方手卡屏幕上缘（两者较上者）
            // 不越过上锚线、近端内容不越过下锚线。旧实现固定 c=0（内容关于视图居中），纵向富余
            // 均分至上下两端，上端富余直接把对方手卡/底板远缘压在顶部 HUD 的提示文本上。
            // 带内仍有富余（cMin ≤ 0 ≤ cMax）时 c 仍取 0，不做多余位移；只有装不下时才推 c（必要时
            // 略增大 hh 缩小视野），优先保证提示文本不被遮挡。
            float depthCorner = Math.max(0.2f, (corners[0] - eyeY) * dY - eyeZ * dZ);
            hh = corners[1] * fieldZoom / depthCorner / aspect;
            c = 0f;
            float[] band = anchorBandNdc(h, Math.max(0f, Math.min(topInsetPx, h * 0.4f)),
                    Math.max(0f, Math.min(bottomInsetPx, h * 0.4f)));
            float[] ext = portraitContentTanExtremes(eyeY, eyeZ, dY, dZ, dZ, -dY,
                    corners[0], OPP_HAND_Y - hY, topZ);
            if (band != null && ext != null && ext[0] > ext[1]) {
                float hhFit = (ext[0] - ext[1]) / (band[0] - band[1]);
                if (hhFit > hh) hh = hhFit;              // 带内装不下：横向贴边让位于不遮挡提示文本
                float cMin = ext[0] - hh * band[0];       // 上缘不越上锚线的最小 c
                float cMax = ext[1] - hh * band[1];       // 下缘不越下锚线的最大 c
                c = Math.max(cMin, Math.min(cMax, 0f));   // c>0 = 视锥上移 = 内容整体下移
            }
        } else {
            // 横屏纵向双锚线取景（用户规格）：上锚线 = 顶部 HUD（血条行 + 聊天/提示行）底边
            //（topInsetPx），下锚线 = 聊天输入框顶边（bottomInsetPx）——双方手卡完整落在两条锚线之间
            //（不裁顶也不裁底）。
            // 半高 hh 取「横向贴边」与「上下锚线恰好容纳」之较大者：宽度合适时通常由横向贴边
            // 主导，hh > hhFit ⇒ 纵向有富余；此时以下锚线为基准解离轴量 c（我方手卡底缘贴聊天
            // 输入框上方），把富余全部留在顶部 → 决斗场/场上卡片/双方手卡整体下移，对方手卡上缘
            // 落到提示栏之下（旧实现以血条下线为基准，富余堆在底部，视觉上决斗场偏高）。
            float[] band = anchorBandNdc(h, Math.max(0f, Math.min(topInsetPx, h * 0.45f)),
                    Math.max(0f, Math.min(bottomInsetPx, h * 0.45f)));
            if (band == null) return s;
            float ndcTop = band[0], ndcBot = band[1];
            // 横向贴边：可交互内容左右恰触边所需的 hh（fRight = hh·aspect·near 承担横向）
            float hhFillW = contentHalfTan(eyeY, eyeZ, dY, dZ) * fieldZoom / aspect;
            // 上下锚线恰好容纳：双方手卡锚点跨度 2tanV 装满 [ndcBot, ndcTop] 所需的 hh（更窄则必裁）
            float hhFit = 2f * tanV / (ndcTop - ndcBot);
            hh = Math.max(hhFillW, hhFit);
            if (hh > 1.6f) hh = 1.6f;
            // 下锚线基准：我方手卡下缘（锚点 -tanV）精确落到 ndcBot，由 (-tanV-c)/hh=ndcBot 解 c；
            // 对方手卡上缘落在 ndcBot + 2tanV/hh ≤ ndcTop（hh≥hhFit 保证），恒在顶部 HUD 下线之内。
            // ndcBot<1 且 hh≥hhFit 保证 c>-hh，fTop=(c+hh)·near 恒正，视锥不翻转。
            c = -tanV - hh * ndcBot;
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

    /** 上/下锚线的 NDC（1=视图顶，-1=视图底）：topIns / botIns 是视图内距像素 */
    private static float[] anchorBandNdc(int h, float topIns, float botIns) {
        if (h <= 1) return null;
        float ndcTop = 1f - 2f * topIns / h;
        float ndcBot = -1f + 2f * botIns / h;
        if (ndcTop < 0.05f) ndcTop = 0.05f;
        if (ndcBot > ndcTop - 0.1f) ndcBot = ndcTop - 0.1f;
        return new float[]{ndcTop, ndcBot};
    }

    /**
     * 横屏有效俯仰角（用户规格：全面屏 17~24:9 的决斗场倾斜度/对方场卡片可读性向 16:9
     * 老式实体 home 键手机、4:3 平板完全统一，并在卡片放大后保持同一个倾斜度）。
     * <p>
     * 根因：横屏决斗场视图只有屏宽 0.7（左侧卡片详情面板常驻），而 HUD（血条行含 50dp 下限、
     * 提示行、输入行）是 dp 定值：屏幕越宽矮（全面屏纵向 dp 高只 375~430dp，16:9 为 540dp），
     * 两条锚线之间的纵向净空占比越小（16:9≈0.74，20:9≈0.70，24:9≈0.67），而横屏取景半高
     * hh = max(横向贴边，2tanV/净空) 在全面屏由净空主导（宽填只 70%，16:9 为 95%）→ 卡片整体
     * 缩小、对方（远端）场卡片投影尺寸从 16:9 的 7.8% 视图高 掉到 7.0~7.3%，ATK/DEF 看不清。
     * <p>
     * 抬高俯角会缩小近/远端深度差，使远端卡片相对变大（远/近比 52° 为 0.836、72° 为 0.912、
     * 平板的 85° 为 0.959）。但历史经验表明：16:9 老式手机与 4:3 平板已对固定仰角满意，
     * **不应让全面屏回归更低仰角**。因此本方法改为直接返回基础仰角，不再自适应抬升——
     * 让所有设备（无论 16:9/17:9/20:9/24:9/4:3）都使用同一倾斜度与卡片大小，视觉一致。
     * <p>
     * 注意：此决策基于「用户偏好」而非技术优化，若未来用户明确反馈需要补偿机制可重新启用。
     */
    private static float landscapeElevation(float baseDeg, int h, float density) {
        // 强制所有横屏设备使用同一仰角，消除 16:9 vs 17~24:9 vs 4:3 之间的视觉差异
        return baseDeg;
    }

    /**
     * 竖屏内容竖向极值的 tan（相对相机视线方向）：返回 [tTop, tBot]（tTop 为上极值、tBot 为下极值）。
     * tTop = max(FIELD_Y_MIN/z=0, OPP_HAND_Y-hY/HAND_Z+hZ)，tBot = min(FIELD_Y_MAX/z=0, corners[0]/0)。
     * 任一极端超出视锥（背面或太近）时返回 null。
     */
    private static float[] portraitContentTanExtremes(float eyeY, float eyeZ,
                                                      float dY, float dZ,
                                                      float upY, float upZ,
                                                      float nearY,
                                                      float oppHandY, float handTopZ) {
        float tFar = tanOfAxis(eyeY, eyeZ, FieldGeometry.FIELD_Y_MIN, 0f, dY, dZ, upY, upZ);
        float tOpp = tanOfAxis(eyeY, eyeZ, oppHandY, handTopZ, dY, dZ, upY, upZ);
        float tNear = tanOfAxis(eyeY, eyeZ, FieldGeometry.FIELD_Y_MAX, 0f, dY, dZ, upY, upZ);
        float tCorner = tanOfAxis(eyeY, eyeZ, nearY, 0f, dY, dZ, upY, upZ);
        float maxT = Float.NEGATIVE_INFINITY;
        if (Float.isFinite(tFar)) maxT = Math.max(maxT, tFar);
        if (Float.isFinite(tOpp)) maxT = Math.max(maxT, tOpp);
        if (maxT == Float.NEGATIVE_INFINITY) return null;
        float minT = Float.POSITIVE_INFINITY;
        if (Float.isFinite(tNear)) minT = Math.min(minT, tNear);
        if (Float.isFinite(tCorner)) minT = Math.min(minT, tCorner);
        if (minT == Float.POSITIVE_INFINITY) return null;
        return new float[]{maxT, minT};
    }

    /** 世界点 (y,z) 相对相机视线方向的高度 tan = up·ray / depth，up=(0,dZ,-dY)（由实际视线
     *  方向 d 与 lookAt 世界 up=(0,0,1) 推出的相机上方向，与 mProj 的离轴基准同一条轴；
     *  用理想俯角 (0,-sinθ,cosθ) 会因 bisector 偏离俯仰方向而产生取景高估误差） */
    private static float tanOfAxis(float eyeY, float eyeZ, float y, float z,
                                   float dY, float dZ, float upY, float upZ) {
        float vy = y - eyeY, vz = z - eyeZ;
        float depth = vy * dY + vz * dZ;
        if (depth < 0.2f) return Float.NEGATIVE_INFINITY;
        return (vy * upY + vz * upZ) / depth;
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
        effElevationDeg = s.effectiveElevation;

        // 离轴（顶部内缩）透视视锥：bottom/top 非对称由 frustumHH 承担（含顶部内缩放大）。
        // 横向半宽必须与竖向同一基准 frustumHH（而非 tanV），保证横竖缩放因子一致——
        // 否则 frustumHH(=2tanV/(1+ndcTop))>tanV 会把卡片矩形横向拉宽（比例失真）。用 frustumHH*aspect
        // 使场地位于等距（isotropic）视锥内，卡片严格保持 177:254 原始比例；GameFieldView 宽度仍由
        // layout_game_right 决定（match_parent），横向自然留白属正常取景，不再以拉伸换取贴边。
        float near = CAM_NEAR;
        // XML 几何微调（与 FieldEditPreview.computeEditCamera 同步以保证所见即所得）：
        // field_length 乘算竖向半高（纵向拉伸）、field_height 叠加离轴量 c（整体上下平移）、
        // field_width 乘算横向半宽（横向拉伸）；三者默认 1.0/0.0/1.0 时与旧取景完全一致。
        float vHH = s.frustumHH * view.fieldLength;
        float vc = s.frustumC + view.fieldHeight;
        float fTop = (vc + vHH) * near;
        float fBottom = (vc - vHH) * near;
        float fRight = s.frustumHH * aspect * view.fieldWidth * near;
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
     * 对方手卡屏幕上缘的屏幕 y（像素，相对视图左上角）：只读查询原语（供需要贴对方手卡
     * 定位的覆盖层使用）。聊天/提示行已不再据此平移——提示栏恒在 gameTopInfo 布局位置，
     * 由恒定上锚线保证不被手卡遮挡。相机未就绪时回退为当前顶部内缩量。
     */
    float computeOpponentHandTopScreenY() {
        // 必须用解算后的有效仰角（横屏可能已按全面屏名义挤占度抬高），否则 billboard 极值推导与实机不符
        float th = (float) Math.toRadians(effElevationDeg);
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
