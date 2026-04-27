# 能源基礎

所有機器都需要能源才能運作。本頁介紹發電機與能源傳輸。

---

## 發電機

### 太陽能發電機

白天放在露天處自動發電，免費且無燃料消耗，初期最推薦。

**合成配方**（在進階工作台上製作）：

| | 第 1 格 | 第 2 格 | 第 3 格 |
|:---:|:---:|:---:|:---:|
| **第 1 排** | ![玻璃](https://minecraft.wiki/images/Invicon_Glass.png) 玻璃 | ![玻璃](https://minecraft.wiki/images/Invicon_Glass.png) 玻璃 | ![玻璃](https://minecraft.wiki/images/Invicon_Glass.png) 玻璃 |
| **第 2 排** | ![銅錠](https://minecraft.wiki/images/Invicon_Copper_Ingot.png) 銅錠 | ![紅石](https://minecraft.wiki/images/Invicon_Redstone.png) 紅石 | ![銅錠](https://minecraft.wiki/images/Invicon_Copper_Ingot.png) 銅錠 |
| **第 3 排** | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 |

> 💡 放在開闊的地方，上方不要有方塊遮擋天空。

### 燃煤發電機

放入煤炭或木炭就能持續發電，適合夜間或地下基地使用。

**合成配方**（在進階工作台上製作）：

| | 第 1 格 | 第 2 格 | 第 3 格 |
|:---:|:---:|:---:|:---:|
| **第 1 排** | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 |
| **第 2 排** | ![煤炭](https://minecraft.wiki/images/Invicon_Coal.png) 煤炭 | ![熔爐](https://minecraft.wiki/images/Invicon_Furnace.png) 熔爐 | ![煤炭](https://minecraft.wiki/images/Invicon_Coal.png) 煤炭 |
| **第 3 排** | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 | ![紅石](https://minecraft.wiki/images/Invicon_Redstone.png) 紅石 | ![鐵錠](https://minecraft.wiki/images/Invicon_Iron_Ingot.png) 鐵錠 |

> 💡 和太陽能發電機搭配使用，白天靠太陽能、夜晚靠燃煤，24 小時不斷電。

---

## 進階發電

隨著科技進展，你可以解鎖更強力的發電設備：

| 發電設備 | 說明 |
|---|---|
| 太陽能陣列 | 進階太陽能，輸出更高 |
| 聚變反應爐 | 高產能聚變發電 |

---

## 能源傳輸

科技插件支援**兩種供電方式**，可以混搭使用：

### 方式一：節點輻射（新手推薦 ⭐）

**能源節點周圍 5 格範圍內的機器自動吸電，不需要貼線纜！**

```
       [發電機]───[線纜]───[能源節點]
                              ↓
                        5 格輻射範圍
                              ↓
        [電爐]  [粉碎機]  [壓縮機]  ← 全部自動吸電
        （附近隨便放，不用接線）
```

操作步驟：
1. 放一台發電機，旁邊鋪幾格導能線纜
2. 把**能源節點**接在線纜末端
3. 在節點的 5 格範圍內（11×11×11 立方體）**隨便放機器** — 全部自動通電

適合新手：不必精心規劃線路，想放哪台就放哪台，只要在節點附近就能運作。

### 方式二：線纜直連（老手精細控制）

- **導能線纜**：把發電機接到機器的電線。兩種合成方式：
  - **工作台**：鐵錠 ×6 + 銅錠 ×1 + 紅石 ×2（`IRI / ICI / IRI`）
  - **組裝機**：銅線 ×1 + 鐵板 ×1（前期用工作台合成即可，有了組裝機後更省材料）
- 機器**直接貼著線纜**才能吸電（或透過線纜接到其他能源來源）
- 線纜有 **8 格 BFS 傳輸上限**（每段線纜算 1 格深度），能源節點會**重置深度**當放大器使用

適合老手：想精確控制電流走向、做長距離傳輸、跨基地供電。

### 兩種方式混搭

最常見的玩法：
- **主幹線**用線纜：發電機 → 線纜 → 節點（長距離遠端布線）
- **工廠內部**靠輻射：把節點丟在工廠中央，周圍機器自動通電

---

## 能源節點

能源網路的中繼站兼「輻射中心」。節點有 3 大功能：

| 功能 | 說明 |
|---|---|
| **輻射供電** | 5 格範圍內的機器自動吸電（不需貼線纜） |
| **BFS 放大** | 傳輸深度遇到節點會重置，讓長距離線路不會斷掉 |
| **能源緩衝** | 節點本身能儲存 6000L 能量當小電池用 |

> 💡 節點需要先被「餵電」才能輻射 — 要麼貼線纜接到發電機，要麼直接放發電機在節點 5 格內（發電機會自動送電給附近節點）。

---

## 電池庫

電池庫可以存儲能源，在發電不足時自動釋放。建議搭配太陽能發電機，白天儲能、夜間釋放。電池庫**支援節點輻射**，在節點附近就能儲放電。

---

## 供電小技巧

- **新手最簡單的電網**：發電機 + 線纜 + 能源節點，節點周圍隨便放機器就能用（詳見 [第一座電網 step-by-step](first-power-grid.md)）
- 越高階的機器吃的電越多，記得擴充發電設備
- 白天用太陽能 + 夜晚用燃煤 = 24 小時不斷電
- ⚡ **純電力版機器**的耗能約為手動版的 **2 倍**，大量部署前請確保發電設施足夠。詳見 [純電力版機器](../machines/electric-machines.md)
- ⚠️ 所有加工類機器通電後會持續消耗**約 1/4 滿載電力**作為待機成本，即使沒有在跑配方也會扣電。不需要的機器記得**暫停**（GUI 右下角按鈕或扳手左鍵）
- 發電機也支援輻射：發電機 5 格內有能源節點時，會**自動送電給節點**（不用特別接線）
