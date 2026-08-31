# 管理員指令（不對玩家公開）

> 這份從 `docs/wiki/commands/README.md` 抽出來，刻意放在 wiki 目錄<b>外面</b>：
> 它不會被建進百科網站，Discord AI 也不會讀到，所以不會拿玩家用不了的指令去回答玩家。

## 管理員指令

以下皆需 `techproject.admin` 權限。

### 物品與進度發放

| 指令 | 說明 |
|---|---|
| `/tech give <物品ID> [玩家]` | 給予指定科技物品/機器（Tab 可補全 ID） |
| `/tech book getall [玩家]` | 給予全解鎖書 |
| `/tech xp add <數量> [玩家]` | 增加研究點數 |
| `/tech tokens view <玩家>` | 查看他人代幣餘額 |
| `/tech tokens add <玩家> <數量>` | 給予能源代幣 |
| `/tech tokens take <玩家> <數量>` | 扣除能源代幣 |
| `/tech tokens set <玩家> <數量>` | 設定代幣餘額 |
| `/tech geo <玩家>` | 發放地質掃描儀 |
| `/tech hazmat <玩家>` | 發放 Hazmat 防護衣四件套 |
| `/tech pipe [數量]` | 給予能量管線（右鍵兩個方塊拉線、左鍵敲掉、右鍵持方塊偽裝） |
| `/tech pipe junction [數量]` | 給予管線轉接頭（4 向分支節點） |
| `/tech pipe purge [半徑]` | 清除附近的孤兒管線 |

### 附魔（測試用）

正式取得走附魔祭壇，此指令僅供測試。手持要附魔的工具後執行 `/tech enchant <附魔ID> <階級 1-3>`。

| 分類 | 附魔ID（顯示名） |
|---|---|
| 工具 | `nano_repair`（奈米修復）、`overclock`（超頻）、`data_mining`（數據採集）、`ley_resonance`（地脈共鳴）、`alchemical_touch`（煉金之觸）、`void_cache`（虛空收納） |
| 武器 | `soul_reaver`（噬魂）、`hemorrhage`（裂傷）、`reapers_verdict`（死神審判）、`chain_lightning`（雷霆連鎖）、`frostbind`（冰封）、`void_singularity`（虛空奇點） |
| 弓 | `soulseeker`（追魂箭）、`detonation_rune`（爆裂符文） |
| 防具 | `phantom_step`（幻影步）、`thorn_mirror`（荊棘鏡壁）、`phoenix_rebirth`（不死鳥涅槃）、`vitality`（生命之樹） |

### 維護與除錯

| 指令 | 說明 |
|---|---|
| `/tech reload` | 熱重載設定 |
| `/tech chicken repair` | 修復全體上線玩家背包/末影箱裡「變成通用口袋雞」與被合堆的雞（離線玩家下次上線自動修） |
| `/tech cleandisplay [半徑]` | 清除附近的展示實體（ItemDisplay/TextDisplay，預設半徑 16、最大 200） |
| `/tech dumpitem` | 印出手上物品的 meta 並與新建參考比對 |
| `/tech dupes` | 掃描載入中區塊的重複背包獨立編號 |
| `/tech estats` | 列出所在世界的 entity 統計（含科技標記數） |
| `/tech stability`（`stab`） | 執行穩定性掃描 |
| `/tech debugtree` | 對準方塊時偵測樹木辨識邏輯 |
| `/tech testfade [glyph]` | 測試 Nexo glyph 能否正常顯示 |
| `/tech butler purge [玩家]` | 清除附近 30 格內或指定玩家的所有管家 |
| `/tech butler purgeorphans` | 清除離線玩家殘留的孤兒漂浮文字 |
| `/tech tutorial <reset\|on\|off\|status> <玩家>` | 對指定玩家操作教學進度（可離線） |

### 冒險者飛船（RTP）

| 指令 | 說明 |
|---|---|
| `/tech rtp` 或 `/tech rtp test` | 對自己啟動測試飛船（直接觸發新玩家流程） |
| `/tech rtp <玩家>` | 對指定線上玩家觸發完整 RTP 飛船流程（含蓋房/領地/管家） |
| `/tech rtp reset` | 重置自己的 RTP 完成狀態 |
| `/tech rtp clear` | 強制清除卡死的飛行狀態與物品內建資料標記 |
| `/tech rtp status` | 查看自己的 RTP 狀態 |

### 建築藍圖工具

| 指令 | 說明 |
|---|---|
| `/tech buildtool`（`savetool`） | 取得建築選取工具 |
| `/tech save <name>` | 儲存選取範圍為 schematic |
| `/tech save butler <name>` | 儲存選取範圍為管家出生點 schematic |
| `/tech load <name>` | 於當前位置貼上（paste）建築 |

### 區域系統

搭配區域選取工具（左鍵位置1、右鍵位置2）使用，可設定 RTP 飛船區域與觀看鏡頭。

| 指令 | 說明 |
|---|---|
| `/tech tool` | 取得區域選取工具 |
| `/tech create <區域ID>` | 由選取範圍建立區域（建好即開啟常駐範圍球） |
| `/tech region <ID> action rtp` | 設定該區域的動作為 RTP |
| `/tech region <ID> show` | 切換該區域的範圍球顯示 |
| `/tech region <ID> delete` | 刪除區域 |
| `/tech set points <ID>` | 把觀看鏡頭定點設在你目前位置 |
| `/tech set launch <ID>` | 把飛船起飛點設在你目前位置 |
| `/tech sphere set <ID> <半徑>` | 在你腳下建立/更新一顆獨立範圍球 |
| `/tech sphere radius <ID> <半徑>` | 修改獨立球半徑 |
| `/tech sphere move <ID>` | 把獨立球搬到你的位置 |
| `/tech sphere delete <ID>` | 刪除獨立球 |
| `/tech sphere list` | 列出所有獨立球 |

### 星球

| 指令 | 說明 |
|---|---|
| `/tech planet <星球>` | 傳送至指定星球（預設 aurelia） |
| `/tech planet info` | 查看所有星球資訊 |
| `/tech planet locateruin` | 定位當前星球的遺跡核心 |
| `/tech planet regenerate` | 重新生成當前星球的出生點結構 |
| `/tech planet debug` / `spawntest` | 星球生成除錯資訊 |

### 迷宮

| 指令 | 說明 |
|---|---|
| `/tech maze opengate` | 強制開啟 Glade 大門（30 秒後自動關閉） |
| `/tech maze closegate` | 強制關閉 Glade 大門 |

---

## 已搬移 / 停用的指令

| 舊指令 | 現況 |
|---|---|
| `/tech skill`、`/tech talent`、`/tech talentpoint`、`/tech tp` | 職業/技能/天賦系統已搬到 TechMMO，改用 `/mmo`（`/mmo skills`、`/mmo tree`、`/mmo points` 等） |
| `/tech adminskill`、`/tech godcd`、`/tech nocd` | 已搬到 `/mmo` |
| `/tech quest` | 任務系統重寫中，之後會放在 TechMMO |
| `/tech top` | 已移除（排行榜歸 TechMMO） |


---



## 稱號佔位符（設定 HUD / 計分板用）

| 佔位符 | 說明 |
|---|---|
| `%techproject_title%` | 玩家目前套用的稱號 |
| `%techproject_title_raw%` | 不含色碼的純文字稱號 |
| `%techproject_title_id%` | 目前套用的成就 ID |
| `%techproject_title_count%` | 已解鎖的稱號數量 |
| `%techproject_title_total%` | 稱號總數 |

