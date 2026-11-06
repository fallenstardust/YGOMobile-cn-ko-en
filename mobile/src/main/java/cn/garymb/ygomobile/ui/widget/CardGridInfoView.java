package cn.garymb.ygomobile.ui.widget;

import static cn.garymb.ygomobile.Constants.ASSET_ATTR_RACE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;

/**
 * 搜索结果宫格条目叠在卡图底部的信息条（用户规格：覆盖式 + 半透明底条，不再单独占卡图下方空间），
 * 三处宫格共用同一份控件树与同一套绑定：
 * 决斗内卡组编辑器的 {@code item_deck_card_grid}、卡查页与卡组编辑页的
 * {@code item_search_card_grid}，都只调 {@link #bind(Card)}，观感与判定口径不会漂移。
 *
 * <p>怪兽两行：第一行均等分三格 = 星级图标与数字<b>重叠成一个</b>（星星贴图按超量取 star_rank、
 * 其余取 star_level，数字压在星形正中；连接怪兽该格换成 linkmarkersmall 那套箭头）、属性图标、
 * 种族图标。第二行攻击力/守备力，连接怪兽为「攻击力/LINK-值」。
 * <p>魔法陷阱只有一行：种类图标（魔法/陷阱）+ 子类图标（速攻/永续/装备/场地/反击/仪式），
 * 通常魔法与通常陷阱没有子类贴图，该位自动隐藏；第二行整行 GONE，不再为等高留空位
 * （用户规格：怪兽与魔陷的格子高度不齐可以接受）。
 *
 * <p>字号：数字统一白字，可读性交给不透明纯黑的居中文字阴影（dx/dy=0、radius=3.5，
 * 用户规格：不做描边而是把阴影做得更明显凝实）。两行数字的定大小方式不同：
 * <b>星数跟星形图标边长走</b>（它是压在星形上的读数，必须与星形同比例才能看清，
 * 用户又要求再放大一档）；<b>攻守按定长样本串</b>而不是本张卡的实文本定大小——
 * 4 位数（0~5000）那一档算，于是同一屏里 "?/?"、"400/2000"、"2500/2000" 都是同一字号，
 * 不会忽大忽小。攻守行的字号由宿主定：{@link #setFixedTextSizeSp(float)} 给卡查页与
 * 卡组编辑页钉回原来的 8sp（用户规格：这两页文字大小保持不变），决斗内卡组编辑器则按
 * 比例推 + {@link #setAtkDefTextScale(float)} 单独放大。
 *
 * <p>其余尺寸全部按条宽（= 卡图宽）推导，不写死 dp：第一行占卡高的 1/4（用户规格「可以占到卡图
 * 下半部的一半」），格内再留一圈白，图标边长 = 格内宽与行高取小，箭头那整块 24dp 原生尺寸也按
 * 同一边长等比缩放，所以三格永远同大小且不会被挤出卡面。
 *
 * <p>贴图与行模式同源（{@code textures/extra/attribute_race/} 下的一族 80×80 正方图）。
 * 属性与种族对复合取值（如 幻魔族 = 龙族|魔法使者）取最低位那张贴图：原先只在整值相等时才给图，
 * 这类卡就永远空着一格；枚举简名与贴图名不同的（魔法师族 spellcaster.png）走例外表，
 * 按通用蛇形规则会被拆成 spell_caster.png 而取不到图。
 */
public class CardGridInfoView extends LinearLayout {

    /**
     * 图标贴图缓存：宫格一屏要绑二三十条，同一批小图反复从 assets 解码纯属浪费，
     * 而这些图来自 APK 内部、终身不变，也没有被谁 recycle 的风险
     */
    private static final Map<String, Bitmap> ASSET_ICON_CACHE = new HashMap<>();

    /** item_carddetail_linkmaker_small 那套箭头的原生边长（dp），缩放到图标边长时用 */
    private static final float LINK_ART_BASE_DP = 24f;

    /** 卡高/卡宽 = 列表态卡图的 70:102，与本控件在两种宫格布局里的卡图比例同源 */
    private static final float CARD_H_OVER_W = 102f / 70f;
    /** 图标行高占卡高的比例：卡图下半部的一半（用户规格） */
    private static final float ROW1_OF_CARD = 0.25f;
    /** 格内留白占图标行高的比例：把图标适当缩小，保证星数、属性、种族三格都放得下 */
    private static final float CELL_PAD_OF_ROW1 = 0.06f;
    /** 底条内边距占图标行高的比例：横向/纵向 */
    private static final float PAD_H_OF_ROW1 = 0.1f;
    private static final float PAD_V_OF_ROW1 = 0.06f;
    /**
     * 星数数字字号占星形图标边长的比例：数字是压在星形正中、充当星形上的读数，
     * 所以上限跟着图标走而不跟固定字号走（宿主钉的 8sp 只管攻守行），
     * 否则小卡面上数字会被缩到看不清（用户规格：星数数字再放大一些）
     */
    private static final float LEVEL_TEXT_OF_ICON = 0.8f;
    /** 攻守数字字号占图标行高的比例（再乘宿主的放大倍数） */
    private static final float ATK_TEXT_OF_ROW1 = 0.8f;
    /** 字号回缩下限，避免样本串太宽把字号压成 0 */
    private static final float MIN_TEXT_DP = 3.5f;

    /** 定长样本：星数最多两位 */
    private static final String SAMPLE_LEVEL = "00";
    /** 定长样本：攻守按 4 位数（0~5000）这一档定字号（用户规格） */
    private static final String SAMPLE_ATKDEF = "0000/0000";
    /** 定长样本：连接怪兽那一串多出 "LINK-"，另给一档，保证不撑出卡宽 */
    private static final String SAMPLE_ATKDEF_LINK = "0000/LINK-0";

    private final LinearLayout row1;
    private final LinearLayout row2;
    private final View cellLevel, cellAttr, cellRace, cellType, cellSubType;
    private final View linkArt;
    private final FrameLayout linkBox;
    private final ImageView iconStar;
    private final TextView tvLevel;
    private final ImageView iconAttr;
    private final ImageView iconRace;
    private final ImageView iconType;
    private final ImageView iconSubType;
    private final TextView tvAtkDef;

    /** 上一次量到的条宽（= 卡图宽）；0 表示还没测过，此时只记文本、不动尺寸 */
    private int mWidthPx;
    /**
     * 信息条总高（含上下内边，按怪兽两行这一最高档位给），不依赖是否已布局：
     * 宫格下卡底的赛制独有标识要按它让位，而那个位置在首次绑定时就读得到
     */
    private int mBarHeight;
    /** 攻守数字的额外放大倍数：决斗内卡组编辑器的卡面明显小于卡查页，由宿主单独放大 */
    private float mAtkDefTextScale = 1f;
    /**
     * 攻守行的固定字号（sp）：>0 时不按比例推，卡查页与卡组编辑页用它保持原来的 8sp。
     * 只管攻守行，星数永远跟着图标大小走
     */
    private float mFixedTextSp;
    /** 按行高推出的两行字号上限与攻守实测字号 */
    private float mLevelTextMax, mAtkTextMax, mAtkTextPx;
    /** 星数与攻守各自的可用宽（前者只有 1/3 格，后者整条内宽） */
    private int mLevelAvail, mInnerAvail;
    /** 本条当前是不是连接怪兽那一档（决定攻守用哪条定长样本） */
    private boolean mLinkRow;

    public CardGridInfoView(Context context) {
        this(context, null);
    }

    public CardGridInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        // 覆盖在卡图上的半透底条：取与两页圆角背带同源的 black_a0（#A0000000），
        // 在构造里统一上，两处宫格布局不必各写一遍
        setBackgroundColor(YGOUtil.c(R.color.black_a0));
        LayoutInflater.from(context).inflate(R.layout.view_card_grid_info, this, true);
        row1 = findViewById(R.id.grid_info_row1);
        row2 = findViewById(R.id.grid_info_row2);
        cellLevel = findViewById(R.id.grid_cell_level);
        cellAttr = findViewById(R.id.grid_cell_attr);
        cellRace = findViewById(R.id.grid_cell_race);
        cellType = findViewById(R.id.grid_cell_type);
        cellSubType = findViewById(R.id.grid_cell_subtype);
        linkBox = findViewById(R.id.grid_link_box);
        iconStar = findViewById(R.id.grid_icon_star);
        tvLevel = findViewById(R.id.grid_level);
        iconAttr = findViewById(R.id.grid_icon_attr);
        iconRace = findViewById(R.id.grid_icon_race);
        iconType = findViewById(R.id.grid_icon_type);
        iconSubType = findViewById(R.id.grid_icon_subtype);
        tvAtkDef = findViewById(R.id.grid_atkdef);
        // 箭头那一整块缩到与属性/种族图标同边长：默认基准点就是自身中心，
        // 而它在格内又是居中摆放，所以缩放后仍居中，不需要动 pivot
        linkArt = linkBox.getChildCount() > 0 ? linkBox.getChildAt(0) : null;
    }

    /**
     * 攻守数字相对图标行高的再放大倍数（用户规格：卡组编辑器可以单独放大攻守数字）。
     * 在绑定时设置即可，下一轮测量会按新倍数重算。
     */
    public void setAtkDefTextScale(float scale) {
        if (Math.abs(mAtkDefTextScale - scale) < 0.01f) return;
        mAtkDefTextScale = scale;
        refreshMetrics();
    }

    /**
     * 把攻守行钉在固定字号（sp），不再按行高比例推：卡查页与卡组编辑页沿用原来的 8sp
     * （用户规格：这两页文字大小保持不变，只在卡组编辑器里放大）。传 0 恢复按比例推。
     * 星数数字不受这里影响，它始终按星形图标边长定大小。
     */
    public void setFixedTextSizeSp(float sp) {
        if (Math.abs(mFixedTextSp - sp) < 0.01f) return;
        mFixedTextSp = sp;
        refreshMetrics();
    }

    /**
     * 条宽（= 卡图宽）定下来后才能算尺寸：本条宽只由卡图定、不依赖自身内容，
     * 所以放在测量一开头把行高、格内白、字号写完，再交给 super 量，同一轮就得到正确结果
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w > 0 && w != mWidthPx) {
            mWidthPx = w;
            applyMetrics(w);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** 条宽已知的场合（宿主改倍数/改字号模式）重算一遍 */
    private void refreshMetrics() {
        if (mWidthPx > 0) {
            applyMetrics(mWidthPx);
        }
    }

    /** 由条宽推出行高、底条与格内留白、箭头缩放与两行字号 */
    private void applyMetrics(int widthPx) {
        int row1H = Math.max(1, Math.round(widthPx * CARD_H_OVER_W * ROW1_OF_CARD));
        ViewGroup.LayoutParams lp = row1.getLayoutParams();
        if (lp != null && lp.height != row1H) {
            // 直写已有的 LayoutParams 对象而不调 setLayoutParams：后者会向上发起 requestLayout，
            // 在测量中途只是白多一轮重排，而本轮 super.onMeasure 本来就会重量子项
            lp.height = row1H;
        }
        int padH = Math.max(1, Math.round(row1H * PAD_H_OF_ROW1));
        int padV = Math.max(1, Math.round(row1H * PAD_V_OF_ROW1));
        setPadding(padH, padV, padH, padV);
        int inner = Math.max(1, widthPx - padH * 2);
        // 三格均等分，格内再留一圈白：图标边长 = 格内宽与行高取小，
        // 箭头那套 24dp 原生块按同一边长缩放，三格因此同大小且不会被挤出卡面
        int cellInner = Math.max(1, inner / 3);
        int cellPad = Math.max(1, Math.round(row1H * CELL_PAD_OF_ROW1));
        setCellPadding(cellPad);
        // 图标实际边长 = 格内宽与行高内可用宽取小（与 fitCenter 的结果一致）
        int iconEdge = Math.max(1, Math.min(cellInner - cellPad * 2, row1H - cellPad * 2));
        if (linkArt != null) {
            // 浮点除：整型除在箭头边长小于 24dp 时会直接归零，箭头就画不出来了
            float art = iconEdge / (float) YGOUtil.dp2px(LINK_ART_BASE_DP);
            linkArt.setScaleX(art);
            linkArt.setScaleY(art);
        }
        mInnerAvail = inner;
        // 星数可以用满星形宽度（样本串只有两位）
        mLevelAvail = iconEdge;
        mLevelTextMax = iconEdge * LEVEL_TEXT_OF_ICON;
        mAtkTextMax = mFixedTextSp > 0
                ? spToPx(mFixedTextSp) : row1H * ATK_TEXT_OF_ROW1 * mAtkDefTextScale;
        applyTextSize();
        // 条高：上内边 + 图标行 + 攻守行（按实测字高 1.3 倍预留行距）+ 下内边，一律按怪兽两行给
        mBarHeight = padV * 2 + row1H
                + Math.max(1, Math.round(Math.max(mAtkTextPx, 1f) * 1.3f));
    }

    /**
     * 信息条占用的高度（px，含内边），按条宽推出不依赖已否布局；未量到条宽前返 0。
     * 给卡面底部的其它叠加元素（如赛制独有标识）让位用
     */
    public int getBarHeight() {
        return mBarHeight;
    }

    private void setCellPadding(int cellPad) {
        View[] cells = {cellLevel, cellAttr, cellRace, cellType, cellSubType};
        for (View cell : cells) {
            if (cell != null) cell.setPadding(cellPad, cellPad, cellPad, cellPad);
        }
    }

    /**
     * 字号按<b>定长样本</b>定，不看本张卡的实文本：攻守一律按 4 位数那一档、连接档按
     * 「4位/LINK-1」那一档，星数按两位，这样同一屏所有卡片同字号，也不会超出可用宽。
     */
    private void applyTextSize() {
        float level = fitTextPx(tvLevel, SAMPLE_LEVEL, mLevelAvail, mLevelTextMax);
        float atk = fitTextPx(tvAtkDef, mLinkRow ? SAMPLE_ATKDEF_LINK : SAMPLE_ATKDEF,
                mInnerAvail, mAtkTextMax);
        mAtkTextPx = atk;
        setTextSize(tvLevel, level);
        setTextSize(tvAtkDef, atk);
    }

    /** 上限能被可用宽放下就用上限，否则按比例回缩（样本定宽 → 结果与实文本无关）；
     *  上限或可用宽还未算出来（首绑先于首量）时返 0 让 setTextSize 直接跳过，不写垃圾字号 */
    private float fitTextPx(TextView tv, String sample, int availPx, float maxPx) {
        if (maxPx <= 0 || availPx <= 0) return 0f;
        Paint paint = tv.getPaint();
        float keep = paint.getTextSize();
        paint.setTextSize(maxPx);
        float need = paint.measureText(sample);
        paint.setTextSize(keep);
        if (need <= availPx) return maxPx;
        return Math.max(YGOUtil.dp2px(MIN_TEXT_DP), maxPx * availPx / need);
    }

    private void setTextSize(TextView tv, float px) {
        if (px <= 0 || Math.abs(tv.getTextSize() - px) < 0.5f) return;
        // 只在字号真的变了才下：setTextSize 会 requestLayout，每轮测量都喊一嗓子会逼出多余重排
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics());
    }

    /** 按卡片绑定信息条；card 为空时无内容可显，整条收起不占卡图空间 */
    public void bind(Card card) {
        if (card == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        if (card.isType(CardType.Monster)) {
            bindMonster(card);
        } else {
            bindSpellTrap(card);
        }
        // 连接档与非连接档的攻守样本不同，文本换了要重算一次字号
        applyTextSize();
    }

    private void bindMonster(Card card) {
        mLinkRow = card.isType(CardType.Link);
        boolean xyz = card.isType(CardType.Xyz);
        cellType.setVisibility(GONE);
        cellSubType.setVisibility(GONE);
        cellLevel.setVisibility(VISIBLE);
        cellAttr.setVisibility(VISIBLE);
        cellRace.setVisibility(VISIBLE);

        // 连接怪兽该格只留箭头那整块（LINK 值在第二行），非连接的才把数字叠在星形上
        linkBox.setVisibility(mLinkRow ? VISIBLE : GONE);
        iconStar.setVisibility(mLinkRow ? GONE : VISIBLE);
        tvLevel.setVisibility(mLinkRow ? GONE : VISIBLE);
        if (mLinkRow) {
            // 箭头贴图与行模式、卡详同源：同一份 linkmaker 小布局按位点亮
            BaseActivity.showLinkArrows(card, linkBox);
        } else {
            setIcon(iconStar, xyz ? "star_rank.png" : "star_level.png");
            tvLevel.setText(String.valueOf(card.getStar()));
            // 数字统一白字（用户规格），超量与普通只靠贴图区分（star_rank / star_level），
            // 可读性交给 XML 里的纯黑居中阴影；这里显式写回颜色，避开 holder 复用时残留上一张的色
            tvLevel.setTextColor(YGOUtil.c(R.color.white));
        }
        setIcon(iconAttr, attributeName(card.Attribute));
        setIcon(iconRace, raceName(card.Race));

        String atk = card.Attack < 0 ? "?" : String.valueOf(card.Attack);
        row2.setVisibility(VISIBLE);
        tvAtkDef.setVisibility(VISIBLE);
        tvAtkDef.setTextColor(YGOUtil.c(R.color.white));
        if (mLinkRow) {
            tvAtkDef.setText(atk + "/LINK-" + card.getStar());
        } else {
            String def = card.Defense < 0 ? "?" : String.valueOf(card.Defense);
            tvAtkDef.setText(atk + "/" + def);
        }
    }

    private void bindSpellTrap(Card card) {
        mLinkRow = false;
        cellLevel.setVisibility(GONE);
        cellAttr.setVisibility(GONE);
        cellRace.setVisibility(GONE);
        // 魔陷只一行：第二行整行收起（不再留等高空位，怪兽与魔陷格子不齐已被接受）
        row2.setVisibility(GONE);

        // 这两格取不到图就连格一起收起：通常魔法/通常陷阱没有子类贴图，
        // 若只隐图标而留着空格，种类图标会偏在左半格而不是整行居中（用户规格）
        setIconInCell(iconType, cellType, card.isType(CardType.Spell) ? "cardType_spell.png"
                : card.isType(CardType.Trap) ? "cardType_trap.png" : null);
        setIconInCell(iconSubType, cellSubType, subTypeName(card));
    }

    /**
     * 子类图标按行模式同一优先级取一个：速攻/永续/装备/场地/反击/仪式，
     * 通常魔法与通常陷阱落空返回 null，由 {@link #setIcon} 隐藏该位
     */
    private static String subTypeName(Card card) {
        if (card.isType(CardType.QuickPlay)) return "quickplay.png";
        if (card.isType(CardType.Continuous)) return "continuous.png";
        if (card.isType(CardType.Equip)) return "equip.png";
        if (card.isType(CardType.Field)) return "field.png";
        if (card.isType(CardType.Counter)) return "counter.png";
        if (card.isType(CardType.Ritual)) return "ritual.png";
        return null;
    }

    /**
     * 属性图标名：复合属性（衍生物一类多属性位）按枚举声明序取第一个命中的位，
     * 原先用整值比对，复合取值永远匹配不到 → 该格空着
     */
    private static String attributeName(long attribute) {
        for (CardAttribute attr : CardAttribute.values()) {
            if ((attribute & attr.getId()) != 0) {
                return attr.name().toLowerCase(Locale.US) + ".png";
            }
        }
        return null;
    }

    /** 种族图标名：同上按最低命中位取图，贴图名见 {@link #RACE_ASSET_NAMES} */
    private static String raceName(long race) {
        for (CardRace r : CardRace.values()) {
            if ((race & r.value()) != 0) {
                return raceAsset(r);
            }
        }
        return null;
    }

    /**
     * 枚举→贴图名的例外表：一族贴图基本是枚举简名的蛇形写法
     * （WingedBeast → winged_beast.png），但魔法师族的贴图是一个连写单词
     * spellcaster.png，按通用规则会被拆成 spell_caster.png 而取不到图
     * （用户报的魔法师族图标不显示）
     */
    private static final Map<CardRace, String> RACE_ASSET_NAMES = new EnumMap<>(CardRace.class);

    static {
        RACE_ASSET_NAMES.put(CardRace.SpellCaster, "spellcaster.png");
    }

    private static String raceAsset(CardRace race) {
        String override = RACE_ASSET_NAMES.get(race);
        return override != null ? override : toAssetName(race.name()) + ".png";
    }

    private static String toAssetName(String enumName) {
        StringBuilder sb = new StringBuilder(enumName.length() + 4);
        for (int i = 0; i < enumName.length(); i++) {
            char c = enumName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Bitmap assetIcon(Context context, String name) {
        String path = ASSET_ATTR_RACE + name;
        if (ASSET_ICON_CACHE.containsKey(path)) {
            return ASSET_ICON_CACHE.get(path);
        }
        Bitmap bm = BitmapUtil.getBitmapFormAssets(context, path, 0, 0);
        // 缺图也入表（值为 null）：否则每张卡都要再试一次打开 assets
        ASSET_ICON_CACHE.put(path, bm);
        return bm;
    }

    /**
     * 贴图标并同步可见性：取不到图（缺纹理）就把图标收起、该格留空占位，
     * 三格均等分的划分不变，也不会留下 holder 复用的上一张卡的图标
     */
    private void setIcon(ImageView iv, String name) {
        Bitmap bm = name == null ? null : assetIcon(getContext(), name);
        iv.setImageBitmap(bm);
        iv.setVisibility(bm == null ? GONE : VISIBLE);
    }

    /**
     * 魔陷那一行用的版本：取不到图时连整格一起收起。该行的格数本来就随卡片变
     * （通常魔陷只有种类一个图标），隐掉空格后剩下的格占满整行，图标自然居中
     */
    private void setIconInCell(ImageView iv, View cell, String name) {
        Bitmap bm = name == null ? null : assetIcon(getContext(), name);
        iv.setImageBitmap(bm);
        iv.setVisibility(bm == null ? GONE : VISIBLE);
        cell.setVisibility(bm == null ? GONE : VISIBLE);
    }
}
