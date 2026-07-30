# 指令與操作

主指令為 `/tech`（別名 `/techbook`、`/techmc`）。多數玩家指令不需要權限，管理指令則需要 `techproject.admin`。所有子指令都有 tab 補全，忘記用法時直接打 `/tech ` 按 Tab 就能看到候選。

---

## 玩家指令

### 科技書與基礎

| 指令 | 說明 |
|---|---|
| `/tech` 或 `/tech book` | 開啟科技百科 |
| `/tech book get` | 補發一本科技書 |
| `/tech book remember` | 切換「記住頁面」，開啟後下次打開會回到上次瀏覽的頁面 |
| `/tech wrench` 或 `/tech wrench get` | 補發一把科技扳手 |
| `/tech research` | 開啟研究台 |
| `/tech list` | 列出科技資料總覽 |
| `/tech stats` | 查看個人科技統計（解鎖數、研究等級、發電量等） |
| `/tech xp` | 查看自己的研究點數與等級 |
| `/tech achievements` | 開啟成就介面 |
| `/tech search [關鍵字]` | 搜尋科技物品/機器；不帶關鍵字會打開鐵砧搜尋介面 |

### 稱號

| 指令 | 說明 |
|---|---|
| `/tech title` | 查看目前裝備的稱號 |
| `/tech title list` | 查看已解鎖的稱號 |
| `/tech title <稱號ID>` | 裝備指定稱號（需已解鎖） |
| `/tech title clear` | 取消目前稱號 |

### 能源代幣與商店

| 指令 | 說明 |
|---|---|
| `/tech shop` | 開啟能源代幣商店 |
| `/tech tokens` | 查看自己的能源代幣餘額 |
| `/tech tokens view <玩家>` | 查看他人的代幣餘額 |

### 新手教學

| 指令 | 說明 |
|---|---|
| `/tech tutorial` 或 `/tech tutorial status` | 查看自己的教學進度與提示開關 |
| `/tech tutorial gui` | 開啟新手教學面板 |
| `/tech tutorial on` / `off` | 開啟／關閉新手教學提示 |
| `/tech tutorial reset` | 重置教學進度（從第 1 階段開始） |

### 管家

管家需先站在自己的領地內生成（管理員例外）。

| 指令 | 說明 |
|---|---|
| `/tech spawn [名字]` | 在自己領地內生成管家（可指定名字） |
| `/tech butler move` | 把管家召喚到你目前的位置 |
| `/tech butler sethome` | 把管家的駐點設在你目前位置 |
| `/tech butler leave` | 讓管家暫時離開 |
| `/tech butler tutorial` | 請管家播放教學 |
| `/tech butler rename [新名]` | 幫管家改名 |
| `/tech butler remove` | 讓管家退役 |
| `/tech butler particle <特效ID\|off>` | 切換管家粒子特效（VIP 功能，需 `techproject.butler.particle` 權限；不帶參數列出可用特效） |

### 冒險者飛船

飛船降落流程中使用（也可直接用獨立指令 `/agree`、`/skip`）。

| 指令 | 說明 |
|---|---|
| `/tech agree` | 同意降落於當前候選地點 |
| `/tech skip` | 跳過當前候選地點 |

### 安卓工作站編程

先對準自己的安卓工作站方塊（8 格內）再執行。

| 指令 | 說明 |
|---|---|
| `/tech android program list` | 查看目前的程式 |
| `/tech android program add <指令...>` | 在最後面新增一行 |
| `/tech android program set <行> <指令...>` | 修改指定行（行號從 0 起算） |
| `/tech android program remove <行>` | 刪掉指定行 |
| `/tech android program clear` | 清空整個程式 |
| `/tech android program help` | 顯示完整編程教學 |

> 可用指令：`MOVE N/E/S/W/U/D`、`MOVE x y z`、`HARVEST`、`CHOP`、`ATTACK`、`SALVAGE`、`WAIT <1-200>`、`RESET`、`JUMP <行>`。最多 64 行，跑到最後一行會回到開頭重複執行。

### 🤝 全域信任共享

將你的**所有**機器共享給其他玩家操作。被信任的玩家可打開/操作，但**不能拆除**。 
信任同時允許雙方機器互連電網與物流。

| 指令 | 說明 |
|---|---|
| `/tech trust <玩家>` | 將玩家加入你的全域信任清單（適用所有機器，含未來放置的） |
| `/tech untrust <玩家>` | 將玩家從全域信任清單移除 |
| `/tech trustlist` | 查看你的全域信任清單 |

> ℹ️ 不需要對準機器，隨時隨地都可以執行。 
> ⚡ 只要任一方 trust 對方，兩人的機器即可互連電網與物流。

---

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
| `/tech dupes` | 掃描載入中區塊的重複背包 獨立編號 |
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
| `/tech rtp clear` | 強制清除卡死的飛行狀態與 物品內建資料 標記 |
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

> 相關獨立指令：`/helper <問題>`（別名 `/幫手`）為 TechMC AI 科技幫手，可直接詢問科技相關問題。

---

## 操作方式

| 操作 | 說明 |
|---|---|
| 右鍵科技書 | 開啟科技百科 |
| 右鍵機器 | 開啟機器介面 |
| 蹲下 + 右鍵機器 | 查看機器資訊 |
| 右鍵研究台 | 開啟研究介面 |
| 板手 + 左鍵機器 | 拆除機器（僅限主人或管理員） |
| 板手 + 右鍵機器 | 轉動機器方向 |
| 能量管線 + 右鍵兩方塊 | 拉一條電線；左鍵敲掉、右鍵持方塊可偽裝外觀 |
| 雞網 + 右鍵野生雞 | 捕捉為口袋雞（基因雞工程） |
