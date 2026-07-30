# 副本插件教學

這份是給管理員與地圖作者看的教學，不是玩家攻略。

目前這個獨立插件的正式指令是 `/dungeon`，別名是 `/dg`。
有些舊訊息還會顯示 `/tech dg`，那是舊字串，實際以 `/dungeon` 或 `/dg` 為準。

## 1. 快速開始

### 建立一個新副本

1. 建骨架：

```text
/dungeon create lost_factory
```

2. 如果你已經有一張世界地圖要拿來當模板，直接把世界資料夾放到伺服器根目錄，再用：

```text
/dungeon import lost_factory
```

注意：目前 `import` 實作只吃一個參數，代表副本 ID 和世界資料夾名稱要相同。

3. 進入編輯模式：

```text
/dungeon edit lost_factory
```

進入後，快捷欄工具如下：

- 第 1 格：主選單
- 第 2 格：設定出生點
- 第 3 格：設定離開點
- 第 4 格：功能建構器
- 第 5 格：怪物生成器
- 第 6 格：事件觸發器
- 第 7 格：基本設定
- 第 9 格：儲存並退出

4. 做完後儲存：

```text
/dungeon save
```

不存離開：

```text
/dungeon cancel
```

## 2. 哪裡生成怪物

這個插件有兩種做法，先分清楚。

### 做法 A：波次生怪

這種適合一般副本流程，例如第 1 波、第 2 波、Boss 波。

設定方式：

1. `/dungeon edit 副本ID`
2. 右鍵主選單，進入「波次管理」
3. 建一個波次
4. 在波次裡設定：
   `spawn-location`: 這一波怪物的基準生成點
   `mobEntries`: 這一波會刷哪些怪
   `spawnOffset`: 每種怪相對於波次基準點的偏移

實際執行時，系統會以波次的 `spawn-location` 當中心，再疊上每隻怪自己的 `spawnOffset`，最後再加一點隨機散開。

簡單說：

- 波次的 `spawn-location` = 這波大致在哪裡刷
- 每隻怪的 `spawnOffset` = 這隻怪相對站位

### 做法 B：功能建構器生怪

這種適合機關觸發，例如：

- 玩家走到某個房間才刷怪
- 右鍵一個祭壇才刷怪
- 打開寶箱後刷埋伏怪

設定方式：

1. `/dungeon edit 副本ID`
2. 拿第 4 格「功能建構器」
3. 對你要綁定的方塊按右鍵
4. 選功能分類「位置」
5. 選功能「怪物生成器」
6. 再選觸發器，例如：
   `玩家偵測器`
   `右鍵點擊`
   `鑰匙偵測`
   `信號接收器`

怪物生成器的常用欄位：

- `mob-type`: 原版實體類型，例如 `ZOMBIE`
- `mythic-mob-id`: 如果你要刷 MythicMobs，填這個，會優先使用
- `count`: 數量
- `level`: 等級
- `custom-name`: 自訂名稱
- `spawn-radius`: 以這個功能方塊為中心，往外散開的半徑

重點：

- 功能建構器刷怪，是綁在那顆方塊上
- `spawn-radius` 就是從那顆方塊附近散刷
- 如果你要固定點刷怪，就把 `spawn-radius` 設小，例如 `0` 或 `1`

## 3. 走過去幾格就觸發

這就是「玩家偵測器」觸發器。

設定方式：

1. 用功能建構器右鍵目標方塊
2. 選好功能，例如「怪物生成器」或「信號發送器」
3. 觸發器選 `PLAYER_DETECTOR`
4. 設定這三個欄位：

- `distance`: 偵測半徑
- `player-count`: 幾個玩家進入才觸發
- `mob-type`: 通常留空。留空就是偵測玩家

範例：

- `distance: 6.0`
- `player-count: 1`

意思就是：

玩家走到這顆功能方塊中心點半徑 6 格內，就會觸發。

如果你要做「怪物走到這裡才觸發」，也可以用同一個觸發器，但把 `mob-type` 填上去，例如 `ZOMBIE`。

## 4. 手上物品右鍵方塊觸發事件

這個要用 `KEY_ITEM_DETECTOR`。

設定方式：

1. 用功能建構器右鍵目標方塊
2. 選你要執行的功能，例如：
   `信號發送器`
   `怪物生成器`
   `傳送器`
   `門控制器`
3. 觸發器選 `KEY_ITEM_DETECTOR`
4. 設定：

- `key-item-type`: 手上物品材質，例如 `TRIPWIRE_HOOK`、`STICK`、`BLAZE_ROD`

目前這個判定的實作重點是：

- 只看手上物品的 `Material`
- 不看自訂名稱
- 不看 lore
- 不看 NBT
- 不看 `tech_item_id`

所以現在如果你要做「手上拿某物品右鍵這個方塊才觸發」，最穩的做法是：

- 直接選一個不容易撞到的材質
- 例如 `BLAZE_ROD` 當鑰匙棒，或 `TRIPWIRE_HOOK` 當鑰匙

如果你要做到「同材質但只有特定自訂物品才算」，目前這版還不支援，得另外補程式。

## 5. 事件如何串連

這套系統最實用的串法有三種。

### 串法 A：信號發送器 + 信號接收器

這是最穩、最好維護的串法。

做法：

1. 功能 A 選 `SIGNAL_SENDER`
2. 把 `signal-name` 設成同一個名字，例如 `room_1_clear`
3. 功能 B 的觸發器選 `SIGNAL_RECEIVER`
4. 功能 B 的 `signal-name` 也填 `room_1_clear`

效果：

- A 執行後送出信號
- 所有同名的 B、C、D 都會被觸發

這很適合做：

- 刷怪完開門
- 啟動機關後播音效、亮燈、刷下一波
- 撿到鑰匙後解鎖某個祭壇

### 串法 B：延遲、重複、順序、隨機

這些是進階功能類型：

- `DELAYED_FUNCTION`: 延遲後執行另一個功能
- `FUNCTION_REPEATER`: 重複執行另一個功能
- `MULTI_FUNCTION`: 同時執行多個功能
- `FUNCTION_RANDOMIZER`: 隨機選一個功能執行
- `FUNCTION_SEQUENCER`: 每次觸發都跑下一個

最常見例子：

- 玩家進房間 -> 先關門 -> 20 tick 後刷怪 -> 清怪後再開門

做法就是把每個動作拆成獨立功能，然後用 `function-id` 或 `function-ids` 串起來。

### 串法 C：舊式 scripts

副本本體仍然保留 `scripts` 系統，適合做比較大型的流程，例如：

- 進區域後開場白
- 清波次後自動推下一波
- Boss 死後完成副本
- 區域密碼、變數判斷、區域進出

如果你是做單點互動、單點機關，優先用功能建構器。
如果你是做整段流程控制，`scripts` 會更像流程圖。

## 6. 你現在最常用的三個範例

### 範例 1：玩家走進房間就刷怪

目標：玩家走到房間門口 5 格內，刷 6 隻怪。

做法：

1. 功能建構器右鍵門口地板那顆方塊
2. 功能選 `MOB_SPAWNER`
3. 觸發器選 `PLAYER_DETECTOR`
4. 設定：

- `distance: 5.0`
- `player-count: 1`
- `mob-type: ""`
- `count: 6`
- `mythic-mob-id: BlueFlameMage`
- `spawn-radius: 3.0`

### 範例 2：拿著鑰匙右鍵祭壇才開門

目標：玩家拿 `TRIPWIRE_HOOK` 右鍵祭壇，門打開。

做法：

1. 在祭壇方塊上建立功能 A
2. 功能 A 選 `SIGNAL_SENDER`
3. 觸發器選 `KEY_ITEM_DETECTOR`
4. 設定：

- `key-item-type: TRIPWIRE_HOOK`
- `signal-name: altar_unlock`

5. 在門那顆方塊建立功能 B
6. 功能 B 選 `DOOR_CONTROLLER`
7. 觸發器選 `SIGNAL_RECEIVER`
8. 設定：

- `signal-name: altar_unlock`
- `door-action: 解鎖`

### 範例 3：清完怪才開門

目標：一波怪打完才開門。

做法：

1. 建一個刷怪功能
2. 再建一個 `SIGNAL_SENDER`，觸發器選 `MOB_DEATH_COUNTER`
3. 設定：

- `mob-type: ""` 或指定怪物類型
- `kill-count: 6`
- `signal-name: wave_1_clear`

4. 門方塊再建一個 `DOOR_CONTROLLER`
5. 它的觸發器選 `SIGNAL_RECEIVER`
6. `signal-name: wave_1_clear`

## 7. YAML 直接改檔範例

功能建構器的資料會存回插件資料夾裡的 `tech-dungeons.yml`，格式在每個副本底下的 `functions` 區塊。

### 範例：走進去刷 MythicMobs

```yml
lost_factory:
  display-name: "失落工廠"
  template-world: "dungeon_lost_factory"
  spawn-point: [0.5, 65, 0.5, 0, 0]
  functions:
    func_1:
      type: MOB_SPAWNER
      trigger-type: PLAYER_DETECTOR
      target-type: NONE
      location: [120, 64, -30]
      allow-retrigger: false
      options:
        mob-type: ZOMBIE
        mythic-mob-id: BlueFlameMage
        count: 4
        level: 1
        custom-name: ""
        spawn-radius: 3.0
      trigger-options:
        distance: 6.0
        player-count: 1
        mob-type: ""
```

### 範例：拿鑰匙右鍵祭壇送出信號

```yml
lost_factory:
  functions:
    func_2:
      type: SIGNAL_SENDER
      trigger-type: KEY_ITEM_DETECTOR
      target-type: NONE
      location: [132, 65, -12]
      allow-retrigger: false
      options:
        signal-name: altar_unlock
      trigger-options:
        key-item-type: TRIPWIRE_HOOK

    func_3:
      type: DOOR_CONTROLLER
      trigger-type: SIGNAL_RECEIVER
      target-type: NONE
      location: [140, 65, -8]
      allow-retrigger: true
      options:
        door-action: 解鎖
      trigger-options:
        signal-name: altar_unlock
```

### 範例：波次生怪

```yml
lost_factory:
  waves:
    - wave-index: 0
      spawn-delay: 2
      message: "第 1 波開始"
      require-clear: true
      spawn-location: [80.5, 64.0, 20.5]
      mobEntries:
        - entity-type: ZOMBIE
          mythic-mob-id: BlueFlameMage
          count: 3
          level: 1
          spawn-offset: [0.0, 0.0, 0.0]
          custom-name: ""
          equipment: {}
```

## 8. 目前你要知道的限制

- `KEY_ITEM_DETECTOR` 目前只看物品材質，不看名稱或 NBT。
- `PLAYER_DETECTOR` 的中心點就是功能方塊本身。
- `distance` 是半徑，不是長寬高分開。
- 如果你要做大範圍不規則區域，優先用 `scripts` 的 `enter_region` / `leave_region` 類條件，不要硬拿單一方塊半徑去湊。
- 功能建構器資料和副本定義都存在插件資料夾下的 `tech-dungeons.yml`，不是 repo 裡那份範本資源檔。

## 9. 實務建議

- 要刷一整波怪：用 `waves`
- 要做房間機關：用功能建構器
- 要做整段流程控制：用 `signals` 加 `scripts`
- 要做玩家走進某處觸發：用 `PLAYER_DETECTOR`
- 要做手持物右鍵方塊觸發：用 `KEY_ITEM_DETECTOR`
- 要做一連串連動：優先用 `SIGNAL_SENDER` + `SIGNAL_RECEIVER`
