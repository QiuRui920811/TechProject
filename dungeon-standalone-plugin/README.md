# TechDungeonStandalone

已從 TechProject 拆出的獨立副本插件。

## 功能範圍
- 副本隊伍、進出副本、波次、Boss、腳本、排行榜
- Function Builder 編輯器（含 GUI 與快捷欄編輯模式）
- Folia/Luminol 安全排程與 NMS 動態世界載入
- MythicMobs 生怪 / 信號 / 條件整合

## 指令
- `/dungeon list`
- `/dungeon join <id>`
- `/dungeon leave`
- `/dungeon party`
- `/dungeon invite <player>`
- `/dungeon accept`
- `/dungeon ready`
- `/dungeon stuck`
- `/dungeon info <id>`
- `/dungeon top <id>`
- 管理指令：`create/edit/setspawn/setexit/setlobby/setname/settime/setplayers/setcooldown/save/cancel/delete/import/adminlist/reload/forceclose`

## 建置
```bash
mvn -DskipTests package
```
輸出：`target/techdungeon-1.0.0.jar`

## 伺服器安裝
1. 將 `techdungeon-1.0.0.jar` 放到 `plugins/`。
2. 第一次啟動後，插件資料夾會產生 `tech-dungeons.yml`、`dungeon-data.yml`、`dungeon_templates/`。
3. 若要沿用舊資料，將舊插件資料夾中的以下檔案複製過來：
   - `tech-dungeons.yml`
   - `dungeon-data.yml`
   - `dungeon_templates/`

## 注意事項
- 請勿與原本內建副本系統同時啟用，避免事件重複處理。
- `tech_material` / `tech_blueprint` 獎勵在獨立版會以原版 Material 名稱發放（或記錄警告）。
