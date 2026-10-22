package cn.garymb.ygomobile.game;

/**
 * 回放「上一步」逆向倒放的显示位置快照条目：仅记录一张卡在步骤 N 时的渲染位移信息
 * （卡身份 {@code code/owner} + 世界坐标 {@code x/y/z} + 旋转 {@code rotX/rotY/rotZ}）。
 *
 * <p>在 {@link ReplayPlayer} 的 {@code performRestart()} 起始（重算 N-1 权威态之前）抓取当前场上各卡
 * 的 {@code curX/curY/curZ} 与 {@code curRotX/curRotY/curRotZ} 存成快照；重算落定后由
 * {@link GameFieldMotion#applyReverseAnimation} 按 {@code (code, owner)} 就近匹配，把发生位移的卡从
 * N 位置逆向补间回 N-1 位置。不涉及 {@code location/sequence/position} 等模型字段（模型已由重算权威落定）。
 */
public final class ReplayPosSnap {
    public final int code;
    public final int owner;
    public final float x, y, z;
    public final float rotX, rotY, rotZ;

    public ReplayPosSnap(int code, int owner,
                         float x, float y, float z,
                         float rotX, float rotY, float rotZ) {
        this.code = code;
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
    }
}
