# 指令與操作

---

## 指令

### 基本指令

| 指令 | 說明 |
|---|---|
| `/tech` 或 `/tech book` | 開啟科技百科 |
| `/tech book get` | 補發一本科技書 |
| `/tech wrench get` | 補發一把科技板手 |
| `/tech list` | 列出科技資料總覽 |
| `/tech stats` | 查看個人科技統計 |
| `/tech research` | 開啟研究台 |
| `/tech xp` | 查看研究點數與等級 |
| `/tech achievements` | 查看成就進度 |
| `/tech search <關鍵字>` | 搜尋科技物品/機器 |
| `/tech planet <星球>` | 傳送至指定星球 |
| `/tech planet info` | 查看所有星球資訊 |

### 稱號指令

| 指令 | 說明 |
|---|---|
| `/tech title` | 查看目前裝備的稱號 |
| `/tech title list` | 查看已解鎖的稱號 |
| `/tech title <稱號ID>` | 裝備指定稱號 |
| `/tech title clear` | 取消目前稱號 |

### 🤝 機器信任共享

可以將自己的機器共享給其他玩家操作（被信任的玩家可打開/操作，但不能拆除）。

| 指令 | 說明 |
|---|---|
| `/tech trust <玩家>` | 對準機器，將玩家加入這台機器的信任清單 |
| `/tech untrust <玩家>` | 對準機器，將玩家從信任清單移除 |
| `/tech trustlist` | 對準機器，查看機器的信任清單 |
| `/tech trustall <玩家>` | 一鍵將玩家加入你**所有**機器的信任清單 |
| `/tech untrustall <玩家>` | 一鍵從你**所有**機器移除該玩家 |

> ℹ️ 物流與電網仍只認同一主人的機器互聯，信任系統僅影響「打開/操作」權限。

### 管理員指令

| 指令 | 權限 | 說明 |
|---|---|---|
| `/tech book getall [玩家]` | `techproject.admin` | 給予全解鎖書 |
| `/tech xp add <數量> [玩家]` | `techproject.admin` | 增加研究點數 |
| `/tech give <物品ID> [玩家]` | `techproject.admin` | 給予指定科技物品/機器 |
| `/tech reload` | `techproject.admin` | 重新載入設定 |

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
| 雞網 + 右鍵野生雞 | 捕捉為口袋雞（基因雞工程） |
