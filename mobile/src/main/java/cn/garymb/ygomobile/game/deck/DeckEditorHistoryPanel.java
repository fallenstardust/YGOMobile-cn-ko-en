package cn.garymb.ygomobile.game.deck;

import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RotateDrawable;
import android.view.View;
import android.widget.ImageButton;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.deck.DeckHistory;

/**
 * === 决斗内卡组编辑器的撤销 / 重做（与卡组编辑页共用同一份历史）===
 * <p>
 * 自 DeckEditorManager 拆出：本类只做三件事——把侧边按钮列里的 undo/redo 接线并跑旋转动画、
 * 按共享历史 {@link DeckHistory} 的游标刷新两键可用态、把 undo/redo 应用回内存中的
 * {@link DeckInfo}。与卡组编辑页一致：<b>只回退内存与界面，不写 ydk</b>，需要用户再点
 * “保存”落盘；isModified 由“游标是否停在原点”回推（原点在载入完成与保存成功时重置）。
 * <p>
 * 换卡组文件必须换户：{@link #onDeckLoaded()} 会调 {@link DeckHistory#bindDeck(String)}，
 * key 变化即整段清零，避免把上一个卡组的快照 undo 进当前卡组。仅在 UI 线程调用。
 */
class DeckEditorHistoryPanel implements DeckHistory.Listener {

    /** 旋转动画时长与圈数上限，与卡组编辑页 animRotate 保持一致（25s 走完 level 0→10000） */
    private static final int SPIN_DURATION_MS = 25000;
    private static final int SPIN_MAX_LEVEL = 10000;

    private final DeckEditorManager owner;
    private final DeckHistory history = DeckHistory.get();
    private boolean listenerRegistered;

    DeckEditorHistoryPanel(DeckEditorManager owner) {
        this.owner = owner;
    }

    /**
     * 视图绑定后调用（首次进入与旋转重绑共用）：取到本方向的按钮副本、起旋转动画、
     * 接点击并挂上历史监听。
     */
    void setup() {
        // 侧边按钮列双副本：全树 findViewById 按 DFS 先命中当前方向的可见副本，另一副本恒 GONE
        owner.btnDeckUndo = owner.activity.findViewById(R.id.btn_deck_undo);
        owner.btnDeckRedo = owner.activity.findViewById(R.id.btn_deck_redo);
        if (owner.btnDeckUndo != null) {
            startSpinAnimation(owner.btnDeckUndo);
            owner.btnDeckUndo.setOnClickListener(v -> undo());
        }
        if (owner.btnDeckRedo != null) {
            startSpinAnimation(owner.btnDeckRedo);
            owner.btnDeckRedo.setOnClickListener(v -> redo());
        }
        if (!listenerRegistered) {
            history.addListener(this);
            listenerRegistered = true;
        }
        refresh();
    }

    /** 编辑器实例销毁时摘除监听，避免单例历史里残留指向旧视图树的回调 */
    void release() {
        if (!listenerRegistered) return;
        history.removeListener(this);
        listenerRegistered = false;
    }

    /**
     * 卡组载入完成：按当前 ydk 路径换户（路径变化即清零历史），把刚载入的内容记为第一条
     * 快照并设为原点，之后 undo 回到这里时 isModified 会自然归 false。
     */
    void onDeckLoaded() {
        history.bindDeck(owner.currentDeckFilePath);
        history.push(owner.currentDeck);
        history.markOrigin();
    }

    /** 保存 / 另存成功：当前内容成为新的原点 */
    void markOrigin() {
        history.markOrigin();
    }

    /** 卡组内容变化后记一次快照；与游标处内容相同则被 DeckHistory 去重跳过 */
    void recordSnapshot() {
        history.push(owner.currentDeck);
    }

    @Override
    public void onDeckHistoryChanged() {
        refresh();
    }

    void undo() {
        applyState(history.undo());
    }

    void redo() {
        applyState(history.redo());
    }

    /**
     * 把历史快照应用回当前卡组：就地改写三个区的列表（保持 currentDeck 引用不变），
     * 再走统一的界面刷新。只读卡包展示与只读卡组不允许回退，避免改到人机/AI 卡组。
     */
    private void applyState(DeckInfo state) {
        if (state == null) return;
        if (owner.isReadonly || owner.isPackMode) return;
        DeckHistory.apply(owner.currentDeck, state);
        owner.isModified = !history.isAtOrigin();
        if (owner.soundManager != null) {
            owner.soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
        }
        owner.notifyDeckChanged();
    }

    /**
     * 刷新两键：无历史可回退时以 INVISIBLE 占位（保留按钮槽位，避免侧边列抖动）。
     * 包级可见：只读态与卡包展示态切换时由 DeckEditorManagerBase#refreshReadonly 一并调。
     */
    void refresh() {
        boolean editable = !owner.isReadonly && !owner.isPackMode;
        boolean hasHistory = editable && history.size() > 1;
        setButtonState(owner.btnDeckUndo, hasHistory && history.canUndo());
        setButtonState(owner.btnDeckRedo, hasHistory && history.canRedo());
    }

    private void setButtonState(ImageButton button, boolean enabled) {
        if (button == null) return;
        button.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        button.setEnabled(enabled);
    }

    /**
     * 旋转背景动画：与卡组编辑页 undo/redo 同款（rotate.xml 的 background_layer 是
     * RotateDrawable，按 level 0→10000 无限循环）。背景不是预期结构时静默跳过。
     */
    private void startSpinAnimation(ImageButton button) {
        if (button == null || !(button.getBackground() instanceof LayerDrawable)) return;
        Drawable layer = ((LayerDrawable) button.getBackground())
                .findDrawableByLayerId(R.id.background_layer);
        if (!(layer instanceof RotateDrawable)) return;
        ObjectAnimator animator = ObjectAnimator.ofInt(layer, "level", 0, SPIN_MAX_LEVEL);
        animator.setDuration(SPIN_DURATION_MS);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.start();
    }
}
