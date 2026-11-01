package cn.garymb.ygomobile.game.deck;

import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RotateDrawable;
import android.view.View;
import android.widget.ImageButton;

import java.io.File;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.deck.DeckHistory;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.IOUtils;

/**
 * === 决斗内卡组编辑器的共享卡组状态接入（撤销 / 重做 + 与卡组编辑页实时联动）===
 * <p>
 * 自 DeckEditorManager 拆出：一是把侧边按钮列里的 undo/redo 接线并跑旋转动画、按共享历史
 * {@link DeckHistory} 的游标刷新两键可用态、把 undo/redo 应用回内存中的 {@link DeckInfo}；
 * 二是作为编辑器与 {@link DeckHistory} 这条同步总线的适配层：本侧写入时带
 * {@link DeckHistory#PUBLISHER_DECK_EDITOR} 广播，另一侧（卡组编辑页）换卡组或改内容时接住并跟进。
 * <p>
 * 与卡组编辑页口径一致：<b>只回退内存与界面，不写 ydk</b>，需用户再点“保存”落盘；
 * isModified 由“游标是否停在原点”回推（原点在载入完成与保存成功时重置）。
 * <p>
 * 换卡组文件必须换户：{@link #onDeckLoaded()} 会调 {@link DeckHistory#bindDeck(String, int)}，
 * key 变化即整段清零，避免把上一个卡组的快照 undo 进当前卡组。反过来说，key 未变时历史里就躺着
 * 另一侧的未保存修改，因此采纳它就能做到“没打开的那一侧下次打开直接到最后修改状态”。
 * 仅在 UI 线程调用。
 */
class DeckEditorHistoryPanel implements DeckHistory.Listener {

    /** 旋转动画时长与圈数上限，与卡组编辑页 animRotate 保持一致（25s 走完 level 0→10000） */
    private static final int SPIN_DURATION_MS = 25000;
    private static final int SPIN_MAX_LEVEL = 10000;

    private final DeckEditorManager owner;
    private final DeckHistory history = DeckHistory.get();
    private boolean listenerRegistered;
    /**
     * 正在应用另一侧同步过来的内容：期间不向历史写入也不广播，否则两边会互相回响。
     */
    private boolean applyingRemote;

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
        // 重绑（旋转）不重新读盘，而隐藏期间收不到的广播要靠这一次补同步；
        // 内容已一致时 currentMatches 会直接短路，不会重建网格
        syncContentFromSession();
        refresh();
    }

    /** 编辑器实例销毁时摘除监听，避免单例历史里残留指向旧视图树的回调 */
    void release() {
        if (!listenerRegistered) return;
        history.removeListener(this);
        listenerRegistered = false;
    }

    /**
     * 卡组载入完成：先按当前 ydk 路径换户（路径变了即清零并广播切换），再把共享会话里
     * 属于同一份卡组的未保存内容盖到刚读盘的结果上，最后把当前内容记为第一条快照并定为原点。
     * <p>
     * 必须先取共享内容再 bindDeck：bindDeck 在 key 变化时会清零历史，先绑后取就什么都拿不到了；
     * 而 key 未变时 bindDeck 不清零，另一侧的未保存修改就仍然留在历史里等我们采纳。
     */
    void onDeckLoaded() {
        String path = owner.currentDeckFilePath;
        boolean sameDeck = path != null && path.equals(history.getDeckKey());
        DeckInfo shared = sameDeck ? history.current() : null;
        history.bindDeck(path, DeckHistory.PUBLISHER_DECK_EDITOR);
        if (shared != null) {
            // 另一侧（或本侧上一轮会话）已有更新的内存内容：直接加载到最后修改状态，
            // 也不把磁盘版本写进历史，否则会截掉对端的修改链
            applyRemoteContent(shared);
            return;
        }
        history.push(owner.currentDeck, DeckHistory.PUBLISHER_DECK_EDITOR);
        history.markOrigin();
    }

    /** 保存 / 另存成功：当前内容成为新的原点 */
    void markOrigin() {
        history.markOrigin();
    }

    /**
     * 卡组内容变化后记一次快照（与游标处内容相同会被 DeckHistory 去重跳过）；
     * 来自对端同步的刷新不再回写，避免双向回响。
     * <p>
     * 写入前先确认会话归属：若卡组编辑页已切到另一份卡组而本编辑器拒绝跟随（会话 key = 页的 path），
     * 直接 push 会把本编辑器这份卡组的内容写进对方的时间线并广播过去，造成跨卡组污染；
     * 这里由本侧重新换户回自己的卡组，让两边回到同一份（页若有未保存修改会先弹确认）。
     */
    void recordSnapshot() {
        if (applyingRemote) return;
        String path = owner.currentDeckFilePath == null ? "" : owner.currentDeckFilePath;
        if (!path.equals(history.getDeckKey())) {
            history.bindDeck(path, DeckHistory.PUBLISHER_DECK_EDITOR);
        }
        history.push(owner.currentDeck, DeckHistory.PUBLISHER_DECK_EDITOR);
    }

    @Override
    public void onDeckHistoryChanged() {
        refresh();
    }

    /**
     * 卡组编辑页切换到另一份卡组：本编辑器也跟着换。
     * <p>
     * 未保存修改时不静默覆盖，复用与“选择卡组 / 退出编辑”同一套确认弹窗文案；
     * 用户取消则本侧停在原卡组，后续对端的内容广播会因 path 不匹配被自然忽略（不会反复弹窗）。
     * 只读与卡包展示下不接受切换（人机/AI 卡组不允许改）。
     */
    @Override
    public void onDeckKeyChanged(String key, int publisher) {
        if (publisher == DeckHistory.PUBLISHER_DECK_EDITOR) return;
        if (key == null || key.isEmpty() || key.equals(owner.currentDeckFilePath)) return;
        if (owner.isReadonly || owner.isPackMode) return;
        if (owner.isModified) {
            owner.showConfirmDialog(owner.activity.getString(R.string.deck_discard_switch_tip),
                    () -> switchTo(key));
            return;
        }
        switchTo(key);
    }

    /** 卡组编辑页改了当前卡组内容（或游标移动）：以会话为准刷新本侧 */
    @Override
    public void onDeckContentChanged(String key, int publisher) {
        if (publisher == DeckHistory.PUBLISHER_DECK_EDITOR) return;
        syncContentFromSession();
    }

    /**
     * 把共享会话游标处的内容应用到本侧（仅当会话确实属于当前这份卡组且内容不同）。
     */
    private void syncContentFromSession() {
        if (applyingRemote || owner.currentDeck == null) return;
        String path = owner.currentDeckFilePath;
        if (path == null || !path.equals(history.getDeckKey())) return;
        DeckInfo shared = history.current();
        if (shared == null || history.currentMatches(owner.currentDeck)) return;
        applyRemoteContent(shared);
    }

    /** 应用对端内容：只改内存与界面，并标记为已修改（磁盘上并没有这些改动） */
    private void applyRemoteContent(DeckInfo shared) {
        applyingRemote = true;
        try {
            DeckHistory.apply(owner.currentDeck, shared);
            owner.isModified = true;
            owner.notifyDeckChanged();
        } finally {
            applyingRemote = false;
        }
    }

    /** 跟随对端切换到同一份卡组：文件已不存在（被删/移动）则保持现状 */
    private void switchTo(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        owner.applySelectedDeck(path,
                IOUtils.tirmName(file.getName(), Constants.YDK_FILE_EX),
                DeckUtil.getDeckTypeName(path));
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
