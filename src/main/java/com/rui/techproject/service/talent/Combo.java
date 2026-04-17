package com.rui.techproject.service.talent;

/**
 * 施法組合（Wynncraft 風格改版）。
 *
 * <p>輸入只使用「右鍵」一種按鈕，搭配是否正在下蹲：
 * <ul>
 *   <li>一般右鍵 → 內部記為 {@code false}，顯示為「右」</li>
 *   <li>下蹲 + 右鍵 → 內部記為 {@code true}，顯示為「蹲右」</li>
 * </ul>
 *
 * <p>為了減少對 {@link SpellId} 的衝擊，enum 常數名保留 LRL/RLR/LLR/RRL，
 * 但語意已經變成「L = 一般右鍵 (false)、R = 蹲下右鍵 (true)」。顯示字串會
 * 正確反映新輸入方式，不會再出現「左鍵」字樣。
 */
public enum Combo {
    LRL("右 · 蹲右 · 右"),
    RLR("蹲右 · 右 · 蹲右"),
    LLR("右 · 右 · 蹲右"),
    RRL("蹲右 · 蹲右 · 右");

    public final String display;

    Combo(final String display) {
        this.display = display;
    }

    /**
     * 由 3 個 Click (true = 蹲下右鍵, false = 一般右鍵) 判斷組合。
     * 注意 enum 名稱裡的「L」現在代表一般右鍵、「R」代表蹲下右鍵，
     * 對照 JavaDoc 讀才不會搞混。
     */
    public static Combo of(final boolean c1, final boolean c2, final boolean c3) {
        if (!c1 && c2 && !c3) return LRL;
        if (c1 && !c2 && c3) return RLR;
        if (!c1 && !c2 && c3) return LLR;
        if (c1 && c2 && !c3) return RRL;
        return null;
    }
}
