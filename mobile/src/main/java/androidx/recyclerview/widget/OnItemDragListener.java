package androidx.recyclerview.widget;

public interface OnItemDragListener {
    void onDragStart();

    void onDragLongPress(int pos);

    void onDragLongPressEnd();

    void onDragEnd();
}
