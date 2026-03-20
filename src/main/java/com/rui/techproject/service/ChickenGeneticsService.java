package com.rui.techproject.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因雞工程服務 — 管理雞 DNA、繁殖邏輯、資源對照表。
 *
 * <h3>基因模型</h3>
 * 每隻雞擁有 6 個基因位點 (B, C, D, F, S, W)，每位點由兩個等位基因組成：
 * <ul>
 *   <li>大寫 = 顯性 (例如 'B')</li>
 *   <li>小寫 = 隱性 (例如 'b')</li>
 * </ul>
 * DNA 以 12 字元字串表示，例如 {@code "BbCCDdFFSSWw"}。
 *
 * <h3>資源對照</h3>
 * 將 6 位點的隱性同型合子狀態視為 6-bit 整數 (B=bit0, C=bit1 … W=bit5)，
 * 對照到 64 種資源。只有擁有至少一個隱性同型合子位點的雞才能在激發室產出資源。
 */
public final class ChickenGeneticsService {

    /** 基因位點名稱 (與 DNA 字串中的順序對應)。 */
    private static final char[] LOCI = {'B', 'C', 'D', 'F', 'S', 'W'};

    // ── 64 種資源對照表 ───────────────────────────────────
    // 索引 = 6-bit 隱性同型合子旗標 (B=bit0 … W=bit5)
    // 值   = 資源 Material 名稱 (由 buildStackForId 解析)
    private static final String[] RESOURCE_IDS = new String[64];
    private static final String[] RESOURCE_NAMES_ZH = new String[64];

    static {
        // ── Tier 0 (0 個隱性) ──
        r(0,  "FEATHER",             "羽毛");
        // ── Tier 1 (1 個隱性) ──
        r(1,  "BONE",                "骨頭");
        r(2,  "COBBLESTONE",         "鵝卵石");
        r(4,  "DIRT",                "泥土");
        r(8,  "FLINT",               "燧石");
        r(16, "SAND",                "沙子");
        r(32, "WATER_BUCKET",        "水桶");
        // ── Tier 2 (2 個隱性) ──
        r(3,  "COAL",                "煤炭");
        r(5,  "STRING",              "線");
        r(9,  "LEATHER",             "皮革");
        r(17, "SUGAR",               "糖");
        r(33, "SPONGE",              "海綿");
        r(6,  "DIORITE",             "閃長岩");
        r(10, "ANDESITE",            "安山岩");
        r(18, "GRAVEL",              "礫石");
        r(34, "ICE",                 "冰");
        r(12, "GRANITE",             "花崗岩");
        r(20, "CLAY_BALL",           "黏土球");
        r(36, "OAK_LOG",             "橡木原木");
        r(24, "GUNPOWDER",           "火藥");
        r(40, "KELP",                "海帶");
        r(48, "SLIME_BALL",          "黏液球");
        // ── Tier 3 (3 個隱性) ──
        r(7,  "GOLD_INGOT",          "金錠");
        r(11, "NETHERRACK",          "地獄石");
        r(19, "GLASS",               "玻璃");
        r(35, "LAPIS_LAZULI",        "青金石");
        r(13, "IRON_INGOT",          "鐵錠");
        r(21, "iron_dust",           "鐵粉");
        r(37, "GOLD_NUGGET",         "金粒");
        r(25, "RAW_COPPER",          "銅原礦");
        r(41, "COPPER_INGOT",        "銅錠");
        r(49, "CAKE",                "蛋糕");
        r(14, "OBSIDIAN",            "黑曜石");
        r(22, "copper_dust",         "銅粉");
        r(38, "AMETHYST_SHARD",      "紫水晶碎片");
        r(26, "LAVA_BUCKET",         "熔岩桶");
        r(42, "RAW_IRON",            "鐵原礦");
        r(50, "SNOWBALL",            "雪球");
        r(28, "REDSTONE",            "紅石");
        r(44, "CACTUS",              "仙人掌");
        r(52, "IRON_NUGGET",         "鐵粒");
        r(56, "GRAY_DYE",            "灰色染料");
        // ── Tier 4 (4 個隱性) ──
        r(15, "BLACKSTONE",          "黑石");
        r(23, "SOUL_SOIL",           "靈魂土");
        r(27, "BLAZE_ROD",           "烈焰棒");
        r(29, "GHAST_TEAR",          "乖靈之淚");
        r(30, "YELLOW_DYE",          "黃色染料");
        r(39, "SHROOMLIGHT",         "蕈光體");
        r(43, "QUARTZ",              "乖離石英");
        r(45, "BASALT",              "玄武岩");
        r(46, "CRYING_OBSIDIAN",     "哭泣黑曜石");
        r(51, "SOUL_SAND",           "靈魂砂");
        r(53, "ENDER_PEARL",         "乖離珍珠");
        r(54, "NETHER_WART",         "乖離疣");
        r(57, "PHANTOM_MEMBRANE",    "乖離乖靈薄膜");
        r(58, "MAGMA_CREAM",         "岩漿球");
        r(60, "GLOWSTONE_DUST",      "螢光粉");
        // ── Tier 5 (5 個隱性) ──
        r(31, "DIAMOND",             "鑽石");
        r(47, "END_STONE",           "終界石");
        r(55, "PRISMARINE_CRYSTALS", "海晶碎片");
        r(59, "PRISMARINE_SHARD",    "海晶砂粒");
        r(61, "EXPERIENCE_BOTTLE",   "經驗瓶");
        r(62, "EMERALD",             "綠寶石");
        // ── Tier 6 (6 個隱性) ──
        r(63, "NETHERITE_INGOT",     "獄髓錠");
    }

    private static void r(final int index, final String materialId, final String zhName) {
        RESOURCE_IDS[index] = materialId;
        RESOURCE_NAMES_ZH[index] = zhName;
    }

    // ─────────────────────────────────────────────────────
    //  DNA 生成
    // ─────────────────────────────────────────────────────

    /**
     * 為野生雞產生隨機 DNA。
     * 每個位點大約有 10% 機率為異型合子 (Aa)，其餘為同型顯性 (AA)。
     */
    public String generateWildDna() {
        final StringBuilder dna = new StringBuilder(12);
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (final char locus : LOCI) {
            final double roll = rng.nextDouble();
            if (roll < 0.05) {
                // 5%: 同型隱性 (aa)
                dna.append(Character.toLowerCase(locus)).append(Character.toLowerCase(locus));
            } else if (roll < 0.20) {
                // 15%: 異型合子 (Aa 或 aA)
                if (rng.nextBoolean()) {
                    dna.append(locus).append(Character.toLowerCase(locus));
                } else {
                    dna.append(Character.toLowerCase(locus)).append(locus);
                }
            } else {
                // 80%: 同型顯性 (AA)
                dna.append(locus).append(locus);
            }
        }
        return dna.toString();
    }

    // ─────────────────────────────────────────────────────
    //  繁殖 (Punnett Square)
    // ─────────────────────────────────────────────────────

    /**
     * 以龐氏方格 (Punnett Square) 繁殖兩隻雞的後代。
     * 每個位點獨立：從父方隨機取一個等位基因 + 從母方隨機取一個。
     */
    public String breed(final String parentA, final String parentB) {
        if (parentA.length() != 12 || parentB.length() != 12) {
            return generateWildDna();
        }
        final StringBuilder child = new StringBuilder(12);
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            final int offset = i * 2;
            // 從父方取一個等位基因
            final char fromA = rng.nextBoolean() ? parentA.charAt(offset) : parentA.charAt(offset + 1);
            // 從母方取一個等位基因
            final char fromB = rng.nextBoolean() ? parentB.charAt(offset) : parentB.charAt(offset + 1);
            // 慣例：大寫在前
            if (Character.isUpperCase(fromA) || (!Character.isUpperCase(fromB))) {
                child.append(fromA).append(fromB);
            } else {
                child.append(fromB).append(fromA);
            }
        }
        return child.toString();
    }

    // ─────────────────────────────────────────────────────
    //  DNA 分析
    // ─────────────────────────────────────────────────────

    /**
     * 計算 DNA 的 6-bit 隱性旗標。
     * 每個為同型隱性的位點對應一個位元。
     */
    public int recessiveFlags(final String dna) {
        if (dna == null || dna.length() != 12) {
            return 0;
        }
        int flags = 0;
        for (int i = 0; i < 6; i++) {
            final int offset = i * 2;
            if (Character.isLowerCase(dna.charAt(offset)) && Character.isLowerCase(dna.charAt(offset + 1))) {
                flags |= (1 << i);
            }
        }
        return flags;
    }

    /**
     * 計算隱性同型合子的數量 (0–6)，即資源等級。
     */
    public int recessiveCount(final String dna) {
        return Integer.bitCount(recessiveFlags(dna));
    }

    /**
     * 取得此 DNA 對應的資源 ID (Material 或 TechProject 物品 ID)。
     * 若無隱性同型合子且旗標 == 0，則回傳羽毛 (Feather)。
     */
    public String resourceId(final String dna) {
        final int flags = recessiveFlags(dna);
        final String id = RESOURCE_IDS[flags];
        return id != null ? id : "FEATHER";
    }

    /**
     * 取得此 DNA 對應的資源中文名。
     */
    public String resourceNameZh(final String dna) {
        final int flags = recessiveFlags(dna);
        final String name = RESOURCE_NAMES_ZH[flags];
        return name != null ? name : "羽毛";
    }

    /**
     * 判斷此雞是否有至少一個隱性同型合子位點 (即可被激發室使用)。
     */
    public boolean canProduceResource(final String dna) {
        return recessiveFlags(dna) != 0;
    }

    /**
     * 計算激發室的產出效率 (隱性越多越快)。
     * 回傳每次 tick 的生產進度 (0.0 ~ 1.0)。
     * 全顯性 = 0, 1 隱性 = 0.10, 6 隱性 = 0.50。
     */
    public double productionRate(final String dna) {
        final int count = recessiveCount(dna);
        if (count == 0) return 0.0;
        // 全同型隱性的位點越多，異型合子的干擾越少
        final int totalLoci = 6;
        final int heteroCount = countHeterozygous(dna);
        // 基礎速率 + 純度加成
        return 0.08 + (count * 0.04) + ((totalLoci - heteroCount - (totalLoci - count)) * 0.02);
    }

    /**
     * 計算異型合子位點數。
     */
    public int countHeterozygous(final String dna) {
        if (dna == null || dna.length() != 12) return 0;
        int count = 0;
        for (int i = 0; i < 6; i++) {
            final int offset = i * 2;
            final char a = dna.charAt(offset);
            final char b = dna.charAt(offset + 1);
            if (Character.isUpperCase(a) != Character.isUpperCase(b)) {
                count++;
            }
        }
        return count;
    }

    // ─────────────────────────────────────────────────────
    //  DNA 顯示格式化
    // ─────────────────────────────────────────────────────

    /**
     * 將 DNA 格式化為人類可讀的基因型字串。
     * 例如 "BbCCDdFFSSWw" → "[Bb] [CC] [Dd] [FF] [SS] [Ww]"
     */
    public String formatDna(final String dna) {
        if (dna == null || dna.length() != 12) return "???";
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(' ');
            sb.append('[');
            sb.append(dna.charAt(i * 2));
            sb.append(dna.charAt(i * 2 + 1));
            sb.append(']');
        }
        return sb.toString();
    }

    /**
     * 將 DNA 格式化為帶有顏色提示的簡短顯示。
     * 回傳各位點狀態：D = 顯性, r = 隱性 (同型), H = 異型。
     */
    public String formatDnaShort(final String dna) {
        if (dna == null || dna.length() != 12) return "??????";
        final StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            final int offset = i * 2;
            final char a = dna.charAt(offset);
            final char b = dna.charAt(offset + 1);
            if (Character.isLowerCase(a) && Character.isLowerCase(b)) {
                sb.append('r'); // recessive homozygous
            } else if (Character.isUpperCase(a) && Character.isUpperCase(b)) {
                sb.append('D'); // dominant homozygous
            } else {
                sb.append('H'); // heterozygous
            }
        }
        return sb.toString();
    }

    /**
     * 計算資源等級 (Tier 0–6)。
     */
    public int resourceTier(final String dna) {
        return recessiveCount(dna);
    }

    // ─────────────────────────────────────────────────────
    //  靜態存取
    // ─────────────────────────────────────────────────────

    /**
     * 取得全部 64 種資源的 ID 陣列 (只讀用途)。
     */
    public static String[] allResourceIds() {
        return RESOURCE_IDS.clone();
    }

    /**
     * 取得全部 64 種資源的中文名陣列。
     */
    public static String[] allResourceNamesZh() {
        return RESOURCE_NAMES_ZH.clone();
    }

    /**
     * 根據 6-bit 旗標取得資源 ID。
     */
    public static String resourceIdByFlags(final int flags) {
        if (flags < 0 || flags >= 64) return "FEATHER";
        final String id = RESOURCE_IDS[flags];
        return id != null ? id : "FEATHER";
    }

    /**
     * 基因位點名稱陣列。
     */
    public static char[] loci() {
        return LOCI.clone();
    }
}
