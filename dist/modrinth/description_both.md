# Litematica MaterialShare

**English** | [日本語はこの下にあります (Japanese below)](#日本語)

*An unofficial add-on for Litematica, not affiliated with its authors. Fabric, Minecraft 1.18 to 1.19.4.*

---

## English

**Shows who collects which material, and how many, right in Litematica's material list, the same for everybody on the server.**

When you build together, somebody always asks "who takes the redstone?" and "how many oak logs are still missing?". This add-on puts the answer into every row of the material list, and keeps it in step for all players.

### What you can do

**Share the work**
- **Take items per row**: "Take / Drop" for yourself, "+" to assign somebody else (offline players can be found by part of their name). "≡" lists everybody who takes an item.
- **Amounts per person**: turn the mouse wheel over a name to change the collected amount (Shift for bigger steps). Targets and hand-ins to a storage are recorded too.
- **Bulk assign**: hands out the items nobody takes to the people you choose, balanced by amount *and* kind. Resets for everything, one person or one item.
- **Filters**: mine / nobody / unfinished / priority / not mine / needs people / one person.
- **Sub-regions**: a schematic with several parts can be shared part by part.

**See how far you are**
- **Missing items**: the items nobody takes come first, then the biggest gaps.
- **My totals**: everything you still have to collect, added up per material over all schematics.
- **HUD**: a small list of what you still have to collect on the game screen. Drag it anywhere you like.
- **Stats, history, recent activity**: who did what and when. People who did nothing for 3 days are marked.
- **Notices** when materials or a whole schematic are complete, or when somebody assigns you to an item or takes you off.

**Storage boxes**
- Mark two corners with hotkeys, click the chat line that appears, and the area is registered (up to 16 per schematic; they need not touch).
- The areas are drawn in the world as coloured boxes, and only those of the selected schematic are shown.
- Chests, trapped chests, barrels, shulker boxes, hoppers, droppers, dispensers, brewing stands, lecterns and jukeboxes inside the areas are counted, including what lies inside shulker boxes. Items that were already in them count too.
- Put an item you are assigned to into a chest in the area and it is recorded as handed in by you, live, while the chest is open.

**More**
- Raw materials: what it takes to craft the items, as a colour-coded map.
- Spare items you offer to the others, importing what you carry, copying the progress (also for Discord).
- History and undo; deleted schematics and items can be restored from the trash.
- A how-to screen with pictures of the real screens, always available (Litematica's main menu, the tools, or a hotkey).
- Japanese and English. Players who are not on the server are looked up at Mojang for their name and skin.

### Requirements

| Where | What you need |
|---|---|
| Server | Fabric Loader 0.14.21+, Fabric API 0.76.0+, **malilib**, **Syncmatica**, **this mod** (Litematica is not needed) |
| Client | The same, plus **Litematica** (versions: see the table below) |

**Supported versions** (one jar per Minecraft version; use the jar that matches your game):

| Minecraft | Litematica | malilib | Jar |
|---|---|---|---|
| 1.18 | 0.9.x (tested: 0.9.1) | 0.10.x (tested: 0.10.0-dev.26) | `...-0.10.0+1.18.jar` |
| 1.18.1 | 0.10.x (tested: 0.10.4) | 0.11.x (tested: 0.11.8) | `...-0.10.0+1.18.1.jar` |
| 1.18.2 | 0.11.x (tested: 0.11.7) | 0.12.x (tested: 0.12.1) | `...-0.10.0+1.18.2.jar` |
| 1.19 | 0.12.x (tested: 0.12.2) | 0.13.x (tested: 0.13.0) | `...-0.10.0+1.19.jar` |
| 1.19.1 | 0.12.x (tested: 0.12.3) | 0.13.x (tested: 0.13.0) | `...-0.10.0+1.19.1.jar` |
| 1.19.2 | 0.12.x (tested: 0.12.7) | 0.13.x (tested: 0.13.0) | `...-0.10.0+1.19.2.jar` |
| 1.19.3 | 0.13.x (tested: 0.13.2) | 0.14.x (tested: 0.14.0) | `...-0.10.0+1.19.3.jar` |
| 1.19.4 | 0.14.2 or newer, older than 0.15.0 (tested: 0.14.7) | 0.15.2 or newer, older than 0.16.0 (tested: 0.15.4) | `...-0.10.0.jar` |

Syncmatica: any version. Fabric API: 0.76.0 or newer (1.19 and 1.19.1: 0.58.0 or newer; 1.18 and 1.18.1: 0.44.0 or newer).

- Put the **same version** of the jar on the server and on every client. If the versions differ, the reason is shown when joining.
- A schematic must be shared with Syncmatica to be tracked. Open its material list and tracking starts by itself.
- Optional: Mod Menu (opens the settings). The settings are also in Litematica's main menu.

### How to use

1. Share the schematic with Syncmatica and open its material list in Litematica.
2. Start tracking with the button at the top right ("Start sharing" or "Schematic: ...").
3. Decide who takes what in the right-hand column of each row. "Tools" has bulk assigning, missing items, storage boxes, stats, history and more. The how-to screen explains every feature.

### For server owners

`config/litematica_materialshare_server.json` is created on the first start. `maxProjects` (1-64, default 64) sets how many schematics can be shared at once. The data is stored in the world. Everybody with the mod sees all schematics and can edit them; every action is in the history.

### Compatibility

Litematica and malilib screens are only extended with mixin injections, and nothing of other mods is overwritten. The buttons do not overlap at any GUI scale. Tested together with Sodium, Sodium Extra, Reese's Sodium Options and about 80 other mods on the server side (no conflicts found).

---

## 日本語

**「誰が、どの素材を、いくつ集めるか」を、Litematica のマテリアルリストの各行に表示して、サーバーの全員で同じ内容を見られるようにするアドオンです。**

みんなで建築をしていると、「その素材は誰が集めるの?」「オークの原木はあと何個足りない?」を、何度も確認することになります。このアドオンは、その答えをマテリアルリストの各行に出して、全員の画面でそろえます。

### できること

**作業を分ける**
- **各行で担当を決める**: 「担当 / 解除」で自分の担当に。「＋」でほかの人も担当に(オフラインの人も、名前の一部で探せます)。「≡」で、その素材を担当する全員の一覧が開きます。
- **人ごとの数を管理**: 担当者の名前の上でマウスホイールを回すと、集めた数が増減します(Shift で大きく)。目標の数や、倉庫への納品も記録できます。
- **一括割り当て**: 担当のない素材を、量だけでなく種類も考えて、選んだ人にバランスよく振り分けます。全員・1人・1素材ごとのやり直しもできます。
- **絞り込み**: 自分の担当 / 担当なし / 未完了 / 優先 / 自分以外 / 担当不足 / 特定の人。
- **サブリージョンごとの共有**: 複数の部分がある設計図は、部分ごとに担当管理を作れます。

**進み具合を見る**
- **足りない素材**: 担当のいない素材が先に、次に残りの多い素材が並びます。
- **自分の合計**: 自分が集める素材を、すべての設計図にわたって素材ごとに合計します。
- **HUD**: ゲーム画面に、自分が集める素材の残りを表示します。好きな場所にドラッグして置けます。
- **統計・履歴・最近の動き**: 誰がいつ何をしたかが分かります。3日以上動いていない人には印が付きます。
- **お知らせ**: 素材が揃ったとき、設計図が完成したとき、ほかの人があなたを担当にした(外した)ときに知らせます。

**保管場所**
- ホットキーで2つの角に印をつけると、チャットに出るリンクをクリックするだけで、範囲を登録できます(1つの設計図に16か所まで。離れていても構いません)。
- 範囲は、ゲーム内に色つきの枠で表示されます。選んでいる設計図の範囲だけが出ます。
- 範囲の中の、チェスト、トラップチェスト、樽、シュルカーボックス、ホッパー、ドロッパー、ディスペンサー、醸造台、書見台、ジュークボックスの中身を数えます。シュルカーボックスの中の素材も、すでに入っていた素材も含みます。
- 自分が担当している素材を範囲内のチェストに入れると、チェストを開いている間に、納品として記録されます。

**そのほか**
- 原材料の分解図: 素材を作るために必要な材料を、色分けしたマップで表示します。
- 余りの共有、持ち物の取り込み、進み具合のコピー(Discord 用も)。
- 履歴と取り消し。削除した設計図や素材は、ゴミ箱から復元できます。
- 実際の画面の写真つきの使い方の画面を、いつでも読めます(Litematica のメインメニュー、ツール、ホットキー)。
- 日本語と英語に対応。サーバーにいない人も、Mojang から名前とスキンを取得して表示します。

### 必要なもの

| 場所 | 必要なもの |
|---|---|
| サーバー | Fabric Loader 0.14.21 以上、Fabric API 0.76.0 以上、**malilib**、**Syncmatica**、**本MOD**(Litematica は不要) |
| クライアント | 上に加えて、**Litematica**(verは下の表を見てください) |

**対応するver**(Minecraft のverごとに jar が別です。遊んでいるverに合う jar を使ってください):

| Minecraft | Litematica | malilib | jar |
|---|---|---|---|
| 1.18 | 0.9.x(確認: 0.9.1) | 0.10.x(確認: 0.10.0-dev.26) | `...-0.10.0+1.18.jar` |
| 1.18.1 | 0.10.x(確認: 0.10.4) | 0.11.x(確認: 0.11.8) | `...-0.10.0+1.18.1.jar` |
| 1.18.2 | 0.11.x(確認: 0.11.7) | 0.12.x(確認: 0.12.1) | `...-0.10.0+1.18.2.jar` |
| 1.19 | 0.12.x(確認: 0.12.2) | 0.13.x(確認: 0.13.0) | `...-0.10.0+1.19.jar` |
| 1.19.1 | 0.12.x(確認: 0.12.3) | 0.13.x(確認: 0.13.0) | `...-0.10.0+1.19.1.jar` |
| 1.19.2 | 0.12.x(確認: 0.12.7) | 0.13.x(確認: 0.13.0) | `...-0.10.0+1.19.2.jar` |
| 1.19.3 | 0.13.x(確認: 0.13.2) | 0.14.x(確認: 0.14.0) | `...-0.10.0+1.19.3.jar` |
| 1.19.4 | 0.14.2 以上 0.15.0 未満(確認: 0.14.7) | 0.15.2 以上 0.16.0 未満(確認: 0.15.4) | `...-0.10.0.jar` |

Syncmatica はどのverでも可。Fabric API は 0.76.0 以上(1.19 と 1.19.1 は 0.58.0 以上、1.18 と 1.18.1 は 0.44.0 以上)。

- サーバーと全員のクライアントに、**同じver**の jar を入れてください。verが違うと、参加するときに理由が表示されます。
- 担当管理を始めるには、設計図が Syncmatica で共有されている必要があります。マテリアルリストを開くと、自動で始まります。
- 任意: Mod Menu(設定画面を開けます)。設定画面は、Litematica のメインメニューからも開けます。

### 使い方

1. Syncmatica で設計図を共有して、Litematica でマテリアルリストを開きます。
2. 右上のボタン(「共有を開始」または「設計図: …」)で担当管理を始めます。
3. 各行の右側で、担当を決めます。「ツール」に、一括割り当て、足りない素材、保管場所、統計、履歴などがあります。すべての機能は、使い方の画面で説明しています。

### サーバー管理者向け

初回の起動で `config/litematica_materialshare_server.json` が作られます。`maxProjects`(1〜64、初期値 64)で、同時に共有できる設計図の数を決められます。データはサーバーのワールドに保存されます。MODを入れた全員が全プロジェクトを見られ、誰でも編集できます(操作は履歴に残ります)。

### 互換性

Litematica と malilib の画面には mixin の注入だけで機能を足しており、ほかのMODの処理は上書きしません。画面の大きさ(GUI 設定)を変えても、ボタンが重ならないように作っています。Sodium、Sodium Extra、Reese's Sodium Options、サーバー側ではほかの約80個のMODと一緒に動くことを確認しています(競合は見つかっていません)。
