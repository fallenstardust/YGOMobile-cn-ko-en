package cn.garymb.ygomobile.ui.adapters;

import static cn.garymb.ygomobile.Constants.ASSET_ATTR_RACE;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.tubb.smrv.SwipeHorizontalMenuLayout;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.game.deck.CardSearcherManagerBase;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.CardListProvider;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class CardListAdapter extends BaseRecyclerAdapterPlus<Card, BaseViewHolder> implements CardListProvider {
    private final StringManager mStringManager;
    private ImageTop mImageTop;
    private TextView tv_limit_num;
    private LimitList mLimitList;//同时只能适用一种禁卡表，所以不管是常规禁限还是genesys禁限，只能使用一个
    private boolean mItemBg;
    private final ImageLoader imageLoader;
    private boolean mEnableSwipe = false;
    private String mSearchKeyword = null;

    public CardListAdapter(Context context, ImageLoader imageLoader) {
        super(context, R.layout.item_search_card);
        this.imageLoader = imageLoader;
        mStringManager = DataManager.get().getStringManager();
    }

    /**
     * 条目布局按宫格标志选：行模式用 item_search_card（卡图 + 卡名/属性/攻守等文字），
     * 宫格模式用 item_search_card_grid（只有卡图与左上角角标）；
     * 返回类型写全限定名：本类体内简名 BaseViewHolder 被
     * {@link BaseRecyclerAdapterPlus.BaseViewHolder} 遮蔽，与父类签名不匹配
     */
    @Override
    protected com.chad.library.adapter.base.viewholder.BaseViewHolder onCreateDefViewHolder(
            ViewGroup parent, int viewType) {
        return createBaseViewHolder(parent,
                mGrid ? R.layout.item_search_card_grid : R.layout.item_search_card);
    }

    public void setSearchKeyword(String keyword) {
        mSearchKeyword = keyword;
    }

    public String getSearchKeyword() {
        return mSearchKeyword;
    }

    @Override
    public int getCardsCount() {
        return getItemCount();
    }

    @Override
    public Card getCard(int posotion) {
        return getItem(posotion);
    }

    public boolean isShowMenu(View view) {
        if (view != null) {
            Object tag = view.getTag(view.getId());
            if (tag != null && tag instanceof ViewHolder) {
                ViewHolder viewHolder = (ViewHolder) tag;
                return viewHolder.mMenuLayout.isMenuOpen();
            }
        }
        return false;
    }

    // === 搜索结果两种展现（行模式：卡图 + 文字信息；宫格模式：只卡图与禁限/起源点角标）===

    /** 当前是否宫格展现；默认行为 false（用户规格：默认开列表，顶部按钮点一下换宫格） */
    private boolean mGrid;
    /** 宫格单列最小宽（px），列数 = 列表可用宽 / 该值；取行模式的卡图宽 card_small_width 保证两处观感一致 */
    private int mGridMinCell;

    /**
     * 切换搜索结果列的展现形式（宫格/行）。本方法只改标志并重建 holder
     * （两种条目布局的控件树完全不同），布局管理器要另外调
     * {@link #applyListLayout(RecyclerView)} 或直接用 {@link #toggleListLayout(RecyclerView)}
     */
    public void setGridLayout(boolean grid) {
        if (mGrid == grid) return;
        mGrid = grid;
        notifyDataSetChanged();
    }

    public boolean isGridLayout() {
        return mGrid;
    }

    /**
     * 按当前展现模式装配布局管理器：宫格 = GridLayoutManager（列数按可用宽 / 单列最小宽
     * 自适应，宽度还没量出来时先给 1 列，下一轮填结果时再复核）；列表 = 原来的
     * {@link FastScrollLinearLayoutManager}（带右侧快速滚动条）。已挂对类型的则只改列数，
     * 不重建布局管理器（避免滚动位置被摸清）。列数变化会 requestLayout，所以下游
     * 页面要在布局之外（post）调用；单格尺寸不在此计算：宫格条目宽高比由
     * item_search_card_grid 的 dimensionRatio 派生，列宽变化自动跟随
     */
    public void applyListLayout(RecyclerView list) {
        if (list == null) return;
        RecyclerView.LayoutManager lm = list.getLayoutManager();
        if (!mGrid) {
            // GridLayoutManager 也是 LinearLayoutManager 的子类，判断列表态时必须先排除宫格
            boolean linear = lm instanceof LinearLayoutManager && !(lm instanceof GridLayoutManager);
            if (!linear) {
                list.setLayoutManager(new FastScrollLinearLayoutManager(list.getContext()));
            }
            return;
        }
        if (mGridMinCell <= 0) {
            mGridMinCell = context.getResources().getDimensionPixelSize(R.dimen.card_small_width);
        }
        int avail = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        int span = avail > 0 ? Math.max(1, avail / mGridMinCell) : 1;
        if (lm instanceof GridLayoutManager) {
            if (((GridLayoutManager) lm).getSpanCount() != span) {
                ((GridLayoutManager) lm).setSpanCount(span);
            }
        } else {
            list.setLayoutManager(new GridLayoutManager(list.getContext(), span));
        }
    }

    /**
     * 顶部展现模式按钮的入口：翻转列表/宫格两态并成对换上对应的布局管理器
     * （条目布局与布局管理器不能错开，否则宫格布局配线性 LM 会把卡图拉成满宽）
     *
     * @return 切换后的模式（true = 宫格），页面据此刷新按钮图标
     */
    public boolean toggleListLayout(RecyclerView list) {
        setGridLayout(!mGrid);
        applyListLayout(list);
        return mGrid;
    }

    /**
     * 按卡组编辑器的搜索结果排序规则重排（sortSel 与编辑器排序下拉位序同义：
     * 0=星数↑ / 1=攻击↑ / 2=守备↑ / 3=名称↓）；比较器取自 {@link CardSearcherManagerBase}，
     * 三个界面共用同一套规则而不各自实现。返回新列表，不改动传入列表（那是搜索线程的产物）。
     */
    public static List<Card> sortByRule(List<Card> cards, int sortSel) {
        List<Card> sorted = new ArrayList<>(cards == null ? 0 : cards.size());
        if (cards != null) sorted.addAll(cards);
        Collections.sort(sorted, CardSearcherManagerBase.searchComparator(sortSel));
        return sorted;
    }

    /**
     * 结果排序下拉的公共装项（卡查页与卡组编辑页共用，装项与样式只在这里维护一份）：
     * 四个选项与决斗内卡组编辑器的搜索结果排序同源（同一批系统字符串 1370-1373），
     * 位序与 {@link #sortByRule} 的 sortSel 同义；白字单行 12sp，
     * 弹层背景沿用两页禁卡表下拉的 colorNavy。选中监听由页面自己接（重排本页当前结果）
     */
    public static void setupSortSpinner(Context context, Spinner spinner) {
        if (spinner == null) return;
        StringManager sm = DataManager.get().getStringManager();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        items.add(new SimpleSpinnerItem(0, sm.getSystemString(1370, "星数↑")));
        items.add(new SimpleSpinnerItem(1, sm.getSystemString(1371, "攻击↑")));
        items.add(new SimpleSpinnerItem(2, sm.getSystemString(1372, "守备↑")));
        items.add(new SimpleSpinnerItem(3, sm.getSystemString(1373, "名称↓")));
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(context);
        adapter.setColor(YGOUtil.c(R.color.white));
        adapter.setSingleLine(true);
        adapter.setTextSize(12f);
        adapter.set(items);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundResource(R.color.colorNavy);
    }

    public void notifyItemChanged(Card card) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getCard(i).Code == card.Code) {
                notifyItemChanged(i);
            }
        }
    }

    private boolean showAddButtons = true; // 默认显示添加按钮

    public void setShowAddButtons(boolean show) {
        this.showAddButtons = show;
    }

    private int getColor(int id) {
        return context.getResources().getColor(id);
    }

    public void setItemBg(boolean itemBg) {
        this.mItemBg = itemBg;
    }

    public void setLimitList(LimitList limitList) {
        if (limitList.getCreditLimits() != null) {
            AppsSettings.get().setGenesysMode(1);
        } else {
            AppsSettings.get().setGenesysMode(0);
        }
        mLimitList = limitList;
    }

    /**
     * 绑定菜单项的点击事件和滑动功能
     * 为菜单中的添加按钮设置点击监听器，并启用滑动菜单功能
     *
     * @param holder   ViewHolder对象，用于获取视图组件
     * @param position 当前菜单项在列表中的位置索引
     */
    // 注：本类继承 BaseRecyclerAdapterPlus，其嵌套类型 BaseViewHolder（RecyclerView.ViewHolder 子类）
    // 在类体作用域内遮蔽了 import 的 chad BaseViewHolder，所以这两处必须写全限定名
    public void bindMenu(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, int position) {
        if (showAddButtons) {
            // 设置主卡组添加按钮的点击事件监听器
            holder.getView(R.id.btn_add_main).setOnClickListener((v) -> {

                EventBus.getDefault().post(new CardInfoEvent(position, true));
            });
            // 设置副卡组添加按钮的点击事件监听器
            holder.getView(R.id.btn_add_side).setOnClickListener((v) -> {
                EventBus.getDefault().post(new CardInfoEvent(position, false));
            });
        }
    }

    @Override
    protected void convert(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, Card item) {
        // 宫格模式只呈现卡图与角标（用户规格），卡名/属性/攻守与加卡键不属于宫格条目
        if (mGrid) {
            imageLoader.bindImage(holder.getView(R.id.card_image), item, ImageLoader.Type.small);
            bindLimitBadge(holder, item);
            return;
        }
        int position = holder.getBindingAdapterPosition() - getHeaderLayoutCount();
        imageLoader.bindImage(holder.getView(R.id.card_image), item, ImageLoader.Type.small);

        if (mSearchKeyword != null && !mSearchKeyword.isEmpty()) {
            SpannableString highlightedName = highlightKeyword(item.Name, mSearchKeyword);
            holder.setText(R.id.card_name, highlightedName);
        } else {
            holder.setText(R.id.card_name, item.Name);
        }

        if (item.isType(CardType.Monster)) {
            holder.setGone(R.id.layout_atk, false);
            holder.setGone(R.id.layout_def, false);
            holder.setGone(R.id.star_attr_race_scale, false);

            holder.setGone(R.id.card_level, false);
            holder.setGone(R.id.card_attr, false);
            holder.setGone(R.id.icon_race, false);
            holder.setGone(R.id.card_race, false);
            //星数等级
            if (item.isType(CardType.Xyz)) {
                holder.setTextColor(R.id.card_level, getColor(R.color.star_rank));
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "star_rank.png", 0, 0));
            } else {
                holder.setTextColor(R.id.card_level, getColor(R.color.star));
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "star_level.png", 0, 0));
            }
            holder.setText(R.id.card_level, String.valueOf(item.getStar()));

            // 属性
            if (item.Attribute == CardAttribute.Dark.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dark.png", 0, 0));
            if (item.Attribute == CardAttribute.Light.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "light.png", 0, 0));
            if (item.Attribute == CardAttribute.Earth.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "earth.png", 0, 0));
            if (item.Attribute == CardAttribute.Wind.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "wind.png", 0, 0));
            if (item.Attribute == CardAttribute.Water.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "water.png", 0, 0));
            if (item.Attribute == CardAttribute.Fire.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fire.png", 0, 0));
            if (item.Attribute == CardAttribute.Divine.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "divine.png", 0, 0));
            holder.setText(R.id.card_attr, mStringManager.getAttributeString(item.Attribute));

            // 种族
            if (item.Race == CardRace.Warrior.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "warrior.png", 0, 0));
            if (item.Race == CardRace.SpellCaster.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "spellcaster.png", 0, 0));
            if (item.Race == CardRace.Fairy.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fairy.png", 0, 0));
            if (item.Race == CardRace.Fiend.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fiend.png", 0, 0));
            if (item.Race == CardRace.Zombie.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "zombie.png", 0, 0));
            if (item.Race == CardRace.Machine.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "machine.png", 0, 0));
            if (item.Race == CardRace.Aqua.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "aqua.png", 0, 0));
            if (item.Race == CardRace.Pyro.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "pyro.png", 0, 0));
            if (item.Race == CardRace.Rock.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "rock.png", 0, 0));
            if (item.Race == CardRace.WingedBeast.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "winged_beast.png", 0, 0));
            if (item.Race == CardRace.Plant.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "plant.png", 0, 0));
            if (item.Race == CardRace.Insect.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "insect.png", 0, 0));
            if (item.Race == CardRace.Thunder.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "thunder.png", 0, 0));
            if (item.Race == CardRace.Dragon.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dragon.png", 0, 0));
            if (item.Race == CardRace.Beast.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "beast.png", 0, 0));
            if (item.Race == CardRace.BeastWarrior.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "beast_warrior.png", 0, 0));
            if (item.Race == CardRace.Dinosaur.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dinosaur.png", 0, 0));
            if (item.Race == CardRace.Fish.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fish.png", 0, 0));
            if (item.Race == CardRace.SeaSerpent.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "sea_serpent.png", 0, 0));
            if (item.Race == CardRace.Reptile.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "reptile.png", 0, 0));
            if (item.Race == CardRace.Psychic.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "psychic.png", 0, 0));
            if (item.Race == CardRace.DivineBeast.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "divine_beast.png", 0, 0));
            if (item.Race == CardRace.CreatorGod.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "creator_god.png", 0, 0));
            if (item.Race == CardRace.Wyrm.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "wyrm.png", 0, 0));
            if (item.Race == CardRace.Cyberse.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cyberse.png", 0, 0));
            if (item.Race == CardRace.Illusion.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "illusion.png", 0, 0));
            holder.setText(R.id.card_race, mStringManager.getRaceString(item.Race));

            // 攻击力
            holder.setText(R.id.card_atk, (item.Attack < 0 ? "?" : String.valueOf(item.Attack)));

            if (item.isType(CardType.Pendulum)) {
                holder.setGone(R.id.p_scale, false);
                holder.setText(R.id.card_scale, String.valueOf(item.LeftScale));
            } else {
                holder.setGone(R.id.p_scale, true);
            }
            if (item.isType(CardType.Link)) {
                holder.setGone(R.id.card_star, true);
                holder.setGone(R.id.card_level, true);
                holder.setGone(R.id.search_link_arrows, false);
                holder.setText(R.id.card_def, item.getStar() < 0 ? "?" : String.valueOf(item.getStar()));
                holder.setText(R.id.TextDef, "LINK-");
                BaseActivity.showLinkArrows(item, holder.getView(R.id.search_link_arrows));
            } else {
                holder.setGone(R.id.card_star, false);
                holder.setGone(R.id.card_level, false);
                holder.setGone(R.id.search_link_arrows, true);
                holder.setText(R.id.card_def, (item.Defense < 0 ? "?" : String.valueOf(item.Defense)));
                holder.setText(R.id.TextDef, "DEF/");
            }

        } else {
            holder.setGone(R.id.card_star, false);
            if (item.isType(CardType.Spell))
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_spell.png", 0, 0));
            if (item.isType(CardType.Trap))
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_trap.png", 0, 0));

            if (item.onlyType(CardType.Spell)) {
                //holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_spell.png", 0, 0));
                holder.setImageBitmap(R.id.icon_attr, null);
            } else if (item.onlyType(CardType.Trap)) {
                //holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_trap.png", 0, 0));
                holder.setImageBitmap(R.id.icon_attr, null);
            }
            if (item.isType(CardType.QuickPlay)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "quickplay.png", 0, 0));
            } else if (item.isType(CardType.Continuous)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "continuous.png", 0, 0));
            } else if (item.isType(CardType.Equip)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "equip.png", 0, 0));
            } else if (item.isType(CardType.Field)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "field.png", 0, 0));
            } else if (item.isType(CardType.Counter)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "counter.png", 0, 0));
            } else if (item.isType(CardType.Ritual)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "ritual.png", 0, 0));
            }
            holder.setGone(R.id.card_level, true);
            holder.setGone(R.id.card_attr, true);
            holder.setGone(R.id.icon_race, true);
            holder.setGone(R.id.card_race, true);
            holder.setGone(R.id.p_scale, true);
            holder.setGone(R.id.search_link_arrows, true);
            holder.setGone(R.id.layout_atk, true);
            holder.setGone(R.id.layout_def, true);
        }
        bindLimitBadge(holder, item);
        //卡片类型
        holder.setText(R.id.card_type, CardUtils.getAllTypeString(item, mStringManager));

        holder.setText(R.id.card_code, String.format("%08d", item.getCode()));

        // 根据设置控制添加按钮的可见性
        if (item.isType(CardType.Token)) {//衍生物不显示添加按钮
            holder.getView(R.id.ll_add_btn).setVisibility(View.GONE);
        } else {
            holder.getView(R.id.ll_add_btn).setVisibility(showAddButtons ? View.VISIBLE : View.GONE);
        }

        bindMenu(holder, position);
        if (mItemBg) {
            holder.setBackgroundResource(R.id.swipe_layout, R.drawable.list_item_bg);
        }
    }

    /**
     * 禁限 / 起源点(GeneSys)点数角标绑定：行模式与宫格模式共用这一段（两种条目布局的
     * right_top / tv_limit_num 同名），保证两种展现下的判定口径与贴图完全一致。
     */
    private void bindLimitBadge(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, Card item) {
        if (mImageTop == null) {
            mImageTop = new ImageTop(context);
        }
        TextView tv_limit_num = holder.getView(R.id.tv_limit_num);//创建该布局的对象，以便对它的字体颜色，大小，文本进行自由设置
        if (mLimitList != null) {

            holder.setGone(R.id.right_top, false);
            if (mLimitList.check(item, LimitType.Forbidden)) {
                holder.setImageBitmap(R.id.right_top, mImageTop.forbidden);
                tv_limit_num.setText("");
            } else if (mLimitList.check(item, LimitType.Limit)) {
                holder.setImageBitmap(R.id.right_top, mImageTop.limit);
                tv_limit_num.setText("1");
                tv_limit_num.setTextSize(12);
                tv_limit_num.setTextColor(YGOUtil.c(R.color.yellow));
            } else if (mLimitList.check(item, LimitType.SemiLimit)) {
                holder.setImageBitmap(R.id.right_top, mImageTop.semiLimit);
                tv_limit_num.setText("2");
                tv_limit_num.setTextSize(12);
                tv_limit_num.setTextColor(YGOUtil.c(R.color.yellow));
            } else if (mLimitList.check(item, LimitType.GeneSys)) {
                // 统一使用 getGeneSysCredit 取值：与 check(GeneSys) 同口径，命中“宽松同名折叠键”或“卡片自身code”
                Integer creditValue = mLimitList.getGeneSysCredit(item);
                holder.setImageBitmap(R.id.right_top, mImageTop.credits);
                tv_limit_num.setText(creditValue == null ? "---" : creditValue.toString());
                tv_limit_num.setTextSize(creditValue == null ? 8 : ((creditValue > -10 && creditValue < 100) ? 10 : 8));
                tv_limit_num.setTextColor(creditValue == null ? YGOUtil.c(R.color.red) : YGOUtil.c(R.color.holo_blue_bright));
            } else {
                holder.setGone(R.id.right_top, true);
                holder.setText(R.id.tv_limit_num, "");
            }
        } else {
            holder.setGone(R.id.right_top, true);
            holder.setText(R.id.tv_limit_num, "");
        }
    }

    /**
     * 高亮显示文本中的关键词
     * 在文本中查找所有匹配的关键词，并为其添加蓝色背景高亮效果
     * 支持空格分隔的多个关键词
     *
     * @param text 需要处理的原始文本
     * @param keyword 需要高亮的关键词（可以是空格分隔的多个关键词）
     * @return 包含高亮效果的SpannableString对象，如果文本或关键词为空则返回未处理的SpannableString
     */
    private SpannableString highlightKeyword(String text, String keyword) {
        SpannableString spannableString = new SpannableString(text);
        if (text == null || keyword == null || keyword.isEmpty()) {
            return spannableString;
        }
        
        // 按空格分割关键词
        String[] keywords = keyword.trim().split("\\s+");
        
        // 对每个关键词进行高亮处理
        for (String singleKeyword : keywords) {
            if (singleKeyword.isEmpty()) {
                continue;
            }
            
            int startIndex = 0;
            while (startIndex < text.length()) {
                int index = text.indexOf(singleKeyword, startIndex);
                if (index == -1) {
                    break;
                }
                
                int endIndex = index + singleKeyword.length();
                BackgroundColorSpan bgSpan = new BackgroundColorSpan(YGOUtil.c(R.color.item_bg));
                spannableString.setSpan(bgSpan, index, endIndex, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                
                startIndex = endIndex;
            }
        }
        
        return spannableString;
    }
}

class ViewHolder extends BaseRecyclerAdapterPlus.BaseViewHolder {
    ImageView cardImage;
    TextView cardName;
    TextView cardLevel;
    TextView cardattr;
    TextView cardrace;
    TextView cardType;
    TextView cardAtk;
    TextView cardDef;
    TextView TextDef;
    TextView cardScale;
    TextView tv_limit_num;
    ImageView rightImage;
    View layout_atk;
    View layout_def;
    View view_bar;
    View layout_star_attr_race_scale;
    View layout_p_scale;
    View linkArrow;
    TextView codeView;
    View btnMain, btnSide;
    SwipeHorizontalMenuLayout mMenuLayout;

    ViewHolder(View view) {
        super(view);
        view.setTag(view.getId(), this);
        cardImage = $(R.id.card_image);
        cardName = $(R.id.card_name);
        cardType = $(R.id.card_type);
        cardAtk = $(R.id.card_atk);
        cardDef = $(R.id.card_def);
        layout_star_attr_race_scale = $(R.id.star_attr_race_scale);
        layout_p_scale = $(R.id.p_scale);
        cardLevel = $(R.id.card_level);
        cardattr = $(R.id.card_attr);
        cardrace = $(R.id.card_race);
        cardScale = $(R.id.card_scale);
        layout_atk = $(R.id.layout_atk);
        layout_def = $(R.id.layout_def);
        view_bar = $(R.id.view_bar);
        rightImage = $(R.id.right_top);
        tv_limit_num = $(R.id.tv_limit_num);
        codeView = $(R.id.card_code);
        TextDef = $(R.id.TextDef);
        btnMain = $(R.id.btn_add_main);
        btnSide = $(R.id.btn_add_side);
        mMenuLayout = $(R.id.swipe_layout);
        linkArrow = $(R.id.search_link_arrows);
//            File outFile = new File(AppsSettings.get().getCoreSkinPath(), Constants.UNKNOWN_IMAGE);
//            ImageLoader.get().$(context, outFile, cardImage, outFile.getName().endsWith(Constants.BPG), 0, null);
    }
}

