package androidx.recyclerview.widget;

public interface OnItemDragListener {
    void onDragStart();

    /**
     * 长按当场触发（按住满 {@code mLongTime} 且当时未拖动即回调，不等抬手）。
     * <p>手指若在回调之后才起拖，父类会就地退出长按模式并紧接着回调
     * {@link #onDragLongPressEnd()}，宿主据此把本次长按语义（如进批量勾选）从这次手势上撤销。
     */
    void onDragLongPress(int pos);

    /** 本次手势的长按状态结束（抬手/取消，或拖动位移超过阈值而就地退出） */
    void onDragLongPressEnd();

    void onDragEnd();
}
