package android.support.v7.widget.helper;

public interface OnItemDragListener {
    void onDragStart();

    void onDragLongPress(int pos);

    void onDragLongPressEnd();

    void onDragEnd();
}
