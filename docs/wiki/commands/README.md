# 指令與操作

主指令是 `/tech`（也可以打 `/techbook`、`/techmc`）。所有子指令都有 tab 補全 —— 忘記怎麼打就輸入 `/tech ` 再按 Tab，會列出可以用的。

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
| `/tech butler particle <特效ID\|off>` | 切換管家粒子特效（VIP 功能；不帶參數會列出可用的特效） |

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

### 問問題

| 指令 | 說明 |
|---|---|
| `/helper <問題>` | 直接問 AI 科技幫手，別名 `/幫手` |

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
