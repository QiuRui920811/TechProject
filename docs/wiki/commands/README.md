# 指令參考

TechProject 的所有指令和佔位符。

---

## 主指令

```
/tech
```

別名：`/techbook`

---

## 玩家指令

| 指令 | 說明 |
|---|---|
| `/tech book` | 開啟科技百科 GUI |
| `/tech list` | 列出所有科技物品 |
| `/tech stats` | 查看個人科技統計 |
| `/tech achievements` | 查看成就進度 |
| `/tech title` | 查看已解鎖的稱號 |
| `/tech title clear` | 取消目前稱號 |

---

## 管理員指令

需要 `techproject.admin` 權限（預設 OP）。

| 指令 | 說明 |
|---|---|
| `/tech give <物品ID> [數量]` | 給予科技物品 |
| `/tech reload` | 重載所有設定檔 |
| `/tech xp <玩家> <數量>` | 設定玩家研究 XP |
| `/tech research <玩家> <項目>` | 直接解鎖研究項目 |

### 使用範例

```
/tech give iron_dust 64
/tech xp Steve 1000
/tech research Steve quantum_processor
/tech reload
```

---

## 權限

| 權限節點 | 說明 | 預設 |
|---|---|---|
| `techproject.admin` | 管理員指令權限 | OP |

---

## PlaceholderAPI 佔位符

搭配 PlaceholderAPI 使用，可在計分板、Tab 列表等顯示科技資訊。

| 佔位符 | 說明 | 範例輸出 |
|---|---|---|
| `%techproject_title%` | 目前稱號（含色碼） | §7[§f機械新手§7] |
| `%techproject_title_raw%` | 純文字稱號 | 機械新手 |
| `%techproject_title_id%` | 目前套用的成就 ID | first_machine |
| `%techproject_title_count%` | 已解鎖稱號數量 | 15 |
| `%techproject_title_total%` | 稱號總數 | 67 |
| `%techproject_level%` | 研究等級 | 3 |
| `%techproject_xp%` | 累計研究 XP | 2450 |

### 計分板範例

```
%techproject_title% %player_name%
研究等級: %techproject_level%
研究 XP: %techproject_xp%
稱號: %techproject_title_count%/%techproject_title_total%
```

---

## 快捷鍵

| 操作 | 說明 |
|---|---|
| 右鍵科技書 | 開啟科技百科 |
| 右鍵機器 | 開啟機器 GUI |
| Shift + 右鍵機器 | 查看機器資訊 |
| 右鍵研究台 | 開啟研究介面 |
