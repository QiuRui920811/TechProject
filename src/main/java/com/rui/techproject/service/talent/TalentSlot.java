package com.rui.techproject.service.talent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 共享節點配置：15 個位置。所有 6 棵技能樹共用同一套位置與前置關係，
 * 但每棵樹在各位置上填入不同的名稱、數值、粒子主動技能。
 *
 * <p>畫布尺寸 9 寬 × 17 高（x = 0..8, y = 0..16）。寬度剛好等於 chest GUI 的 9 格，
 * 所以<b>完全不需要左右滾動</b> — 玩家只用底部工具列的 ↑ / ↓ 按鈕上下捲動畫布。
 *
 * <p><b>所有連線都是純直向或純橫向</b>：每個前置節點跟子節點必定共用 x 或共用 y。
 * 節點間的距離固定為 3 格，所以每條連線都會剛好鋪 2 片玻璃，
 * 不論橫線或直線都具備相同的視覺厚度 — 美術可以直接按這個骨架畫 HUD。
 *
 * <p>邏輯層級（bottom→top，y 越大越接近底部）：
 * <pre>
 *   x=    1       4       7
 *   y=  1 LRL     UP2     RLR     ← 頂端
 *   y=  4 TL ──── UP1 ──── TR     ← 上層三節點（橫線）
 *   y=  7 LLR     MID             ← 中上
 *   y= 10 LEFT    MID_V   RRL     ← 中層
 *   y= 13 LEFT_L─BASE ─── RIGHT_R ← 三叉（橫線）
 *   y= 16         START            ← 起點（免費）
 * </pre>
 */
public enum TalentSlot {
    // 起點（底部中央，免費）
    START        ("start",          4, 16, TalentNodeType.START,    null),
    // 第一層 Minor（垂直向上）
    MINOR_BASE   ("minor_base",     4, 13, TalentNodeType.MINOR,    null, "start"),
    // 三叉分支（全部跟 MINOR_BASE 共用 y=13，純橫線）
    MINOR_LEFT_L ("minor_left_l",   1, 13, TalentNodeType.MINOR,    null, "minor_base"),
    MINOR_MID    ("minor_mid",      4, 10, TalentNodeType.MINOR,    null, "minor_base"),
    MINOR_RIGHT_R("minor_right_r",  7, 13, TalentNodeType.MINOR,    null, "minor_base"),
    // 左邊支線（全部 x=1，純直線向上）
    NOTABLE_LEFT ("notable_left",   1, 10, TalentNodeType.NOTABLE,  null, "minor_left_l"),
    KEYSTONE_LLR ("keystone_llr",   1,  7, TalentNodeType.KEYSTONE, Combo.LLR, "notable_left"),
    // 右邊支線（全部 x=7，純直線向上）
    KEYSTONE_RRL ("keystone_rrl",   7, 10, TalentNodeType.KEYSTONE, Combo.RRL, "minor_right_r"),
    // 中央通道（全部 x=4）
    NOTABLE_MID  ("notable_mid",    4,  7, TalentNodeType.NOTABLE,  null, "minor_mid"),
    MINOR_UP1    ("minor_up1",      4,  4, TalentNodeType.MINOR,    null, "notable_mid"),
    // 上層三節點：TL/TR 跟 MINOR_UP1 共用 y=4（橫線），MINOR_UP2 跟 MINOR_UP1 共用 x=4（直線）
    NOTABLE_TL   ("notable_tl",     1,  4, TalentNodeType.NOTABLE,  null, "minor_up1"),
    MINOR_UP2    ("minor_up2",      4,  1, TalentNodeType.MINOR,    null, "minor_up1"),
    NOTABLE_TR   ("notable_tr",     7,  4, TalentNodeType.NOTABLE,  null, "minor_up1"),
    // 頂端雙 Keystone（跟各自的 NOTABLE 共用 x，純直線）
    KEYSTONE_LRL ("keystone_lrl",   1,  1, TalentNodeType.KEYSTONE, Combo.LRL, "notable_tl"),
    KEYSTONE_RLR ("keystone_rlr",   7,  1, TalentNodeType.KEYSTONE, Combo.RLR, "notable_tr");

    /** 畫布寬度（x 範圍 0..CANVAS_W-1），等於 chest GUI 寬 → 不需左右滾動。 */
    public static final int CANVAS_W = 9;
    /** 畫布高度（y 範圍 0..CANVAS_H-1），需要上下滾動。 */
    public static final int CANVAS_H = 17;

    public final String slotId;
    public final int x;
    public final int y;
    public final TalentNodeType type;
    public final Combo combo;
    public final List<String> prereqs;

    TalentSlot(final String slotId, final int x, final int y,
               final TalentNodeType type, final Combo combo,
               final String... prereqs) {
        this.slotId = slotId;
        this.x = x;
        this.y = y;
        this.type = type;
        this.combo = combo;
        this.prereqs = prereqs.length == 0
                ? Collections.emptyList()
                : Arrays.asList(prereqs);
    }

    public static TalentSlot bySlotId(final String id) {
        if (id == null) return null;
        for (final TalentSlot s : values()) {
            if (s.slotId.equalsIgnoreCase(id)) return s;
        }
        return null;
    }
}
