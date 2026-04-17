package com.rui.techproject.service.talent;

/**
 * 節點類型：決定花費、等級上限、視覺表現。
 * <ul>
 *   <li>START — 起點，自動解鎖，0 pt</li>
 *   <li>MINOR — 小節點，1 pt × 5 ranks，單純屬性加成</li>
 *   <li>NOTABLE — 重要節點，2 pt × 1 rank，強力被動</li>
 *   <li>KEYSTONE — 核心節點，3 pt × 1 rank，解鎖主動技能</li>
 * </ul>
 */
public enum TalentNodeType {
    START(0, 1),
    MINOR(1, 5),
    NOTABLE(2, 1),
    KEYSTONE(3, 1);

    public final int costPerRank;
    public final int maxRank;

    TalentNodeType(final int costPerRank, final int maxRank) {
        this.costPerRank = costPerRank;
        this.maxRank = maxRank;
    }
}
