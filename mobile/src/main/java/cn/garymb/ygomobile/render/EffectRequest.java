package cn.garymb.ygomobile.render;

/** 一次特效播放请求（SpecEffectOverlay 队列元素），字段与 SpecEffectView.startCard 参数一一对应 */
final class EffectRequest {
    final int type, code, param;
    final float holdFrames, difInit;
    final String text, subText;

    EffectRequest(int type, int code, int param, float holdFrames,
                  String text, String subText, float difInit) {
        this.type = type;
        this.code = code;
        this.param = param;
        this.holdFrames = holdFrames;
        this.text = text;
        this.subText = subText;
        this.difInit = difInit;
    }
}
