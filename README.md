# Litematica MaterialShare

**「誰が、どの素材を、いくつ集めるか」を、サーバーの全員で共有する Litematica のアドオンです。**
(Fabric / Minecraft 1.18・1.18.1・1.18.2・1.19・1.19.1・1.19.2・1.19.3・1.19.4)

建築プロジェクトを複数人で進めるとき、Litematica のマテリアルリストを見ながら「その素材は誰が集めるの?」「あと何個足りない?」を、チャットや口頭で確認していませんか。
このアドオンは、マテリアルリストの各行に**担当者と収集数**を表示して、全員の画面でそろえます。

## できること

- **各行で担当を決める**: 「担当 / 解除」ボタンで自分の担当に。「＋」でほかの人も担当に(オフラインの人も、名前の一部で探せます)。
- **担当ごとの数を管理**: 担当者の名前の上でマウスホイールを回すと、集めた数が増減します(Shift で大きく)。目標の数や、倉庫への納品も記録できます。
- **足りない素材が一目で分かる**: 「足りない素材」画面は、担当のいない素材と、残りの多い素材から順に並びます。
- **絞り込み**: 自分の担当 / 担当なし / 未完了 / 優先 / 自分以外 / 担当不足 / 特定の人。
- **一括割り当て**: 担当のない素材を、量だけでなく種類も考えて、選んだ人にバランスよく振り分けます。やり直しも、人ごと・素材ごとにできます。
- **サブリージョンごとの共有**: 複数の部分(サブリージョン)がある設計図は、部分ごとに担当管理を作れます。
- **履歴と取り消し**: だれが何をしたかが残り、削除した設計図や素材は、ゴミ箱から復元できます。
- **進み具合**: 統計、HUD、完了や割り当ての通知。3日以上動いていない人には印が付きます。
- **保管場所**: チェストのある範囲を(最大16か所)登録すると、ゲーム内に色つきの枠で表示され、中身を数え、自分の担当をチェストに入れると納品として記録されます。
- **使い方の画面**: すべての機能の使い方を、実際の画面の写真つきでいつでも読めます。
- **原材料の分解図**: 素材を作るために必要な材料を、色分けしたマップで表示します。
- **余りの共有 / 持ち物の取り込み / 進捗のコピー(Discord 用など)**。
- **プレイヤーの顔と名前**: サーバーにいない人も、Mojang から名前とスキンを取得して表示します。
- 日本語と英語に対応しています。

## 導入

| 場所 | 必要なもの |
|---|---|
| サーバー | Fabric Loader 0.14.21 以上、Fabric API 0.76.0 以上、**malilib**、**Syncmatica**、**本MOD**(Litematica は不要) |
| クライアント | 上に加えて、**Litematica**(版は下の表を見てください) |

**対応する版**(Minecraft の版ごとに jar が別です。遊んでいる版に合う jar を使ってください):

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

Syncmatica はどの版でも可。Fabric API は 0.76.0 以上(1.19 と 1.19.1 は 0.58.0 以上、1.18 と 1.18.1 は 0.44.0 以上)。

- サーバーとクライアントの**両方**に、同じ jar を入れます。
- 担当管理を始めるには、設計図が **Syncmatica で共有**されている必要があります。共有されていれば、マテリアルリストを開くと自動で担当管理が始まります。
- 任意: Mod Menu(設定画面を開けます)。
- 設定画面は、Litematica のメインメニューからも開けます。

## 使い方

1. Syncmatica で設計図を共有して、Litematica でマテリアルリストを開きます。
2. 右上のボタン(「共有を開始」または「設計図: …」)で担当管理を始めます。
3. 各行の右側で、担当を決めます。画面上部の「ツール」から、一括割り当て・統計・履歴などを使います。

## サーバー管理者向け

初回の起動で `config/litematica_materialshare_server.json` が作られます。

| 項目 | 内容 |
|---|---|
| `maxProjects` | 同時に共有できる設計図の数(1〜64)。初期値は 64 |

データはサーバーのワールドに保存されます。MODを入れた全員が全プロジェクトを見られ、誰でも編集できます(操作は履歴に残ります)。

## 互換性

Litematica / malilib の画面には mixin の `@Inject` だけで追加しており、他の mod の処理を上書きしません。Sodium、Sodium Extra、Reese's Sodium Options と一緒に動くことを確認しています。画面の大きさ(GUI 設定)を変えても、ボタンが重ならないように作っています。


---

# Litematica MaterialShare

**An add-on for Litematica that shows who collects which material, and how much, to everybody on the server.**
(Fabric / Minecraft 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4)

Building together, you keep asking "who takes the redstone?" and "how many oak logs are still missing?" in chat.
This add-on puts **who takes an item and how much they have collected** right into each row of Litematica's material list, the same for every player.

## Features

- **Take items per row**: "Take / Drop" for yourself, "+" to assign somebody else (offline players can be found by part of their name).
- **Amounts per person**: turn the mouse wheel over a name to change the collected amount (Shift for bigger steps). Targets and hand-ins to a storage are recorded too.
- **See what is missing**: the "Missing items" screen lists the items nobody takes first, then the biggest gaps.
- **Filters**: mine / nobody / unfinished / priority / not mine / not enough people / one person.
- **Bulk assign**: hands out the items nobody takes to the people you choose, balanced by amount *and* kind. Reset everything, one person or one item.
- **Sub-regions**: a schematic with several parts can be shared part by part.
- **History and undo**: who did what is kept; deleted schematics and items can be restored from the trash.
- **Progress**: stats, HUD, notifications for completed and assigned items. People who did nothing for 3 days are marked.
- **Storage boxes**: register where your chests are (up to 16 areas); they are drawn in the world as coloured boxes, the chests are counted, and putting items you are assigned to into them is recorded as handed in.
- **A how-to screen** with pictures of the real screens, always available.
- **Raw material map**: what it takes to craft the items, as a colour-coded map.
- **Spare items, importing what you carry, copying progress (for Discord and more).**
- **Faces and names**: players who are not on the server are looked up at Mojang for their name and skin.
- Japanese and English.

## Installation

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

- Put the same jar on the server **and** on every client.
- A schematic must be **shared with Syncmatica** to be tracked; open its material list and tracking starts by itself.
- Optional: Mod Menu (opens the settings).
- The settings screen is also in Litematica's main menu.

## How to use

1. Share the schematic with Syncmatica and open its material list in Litematica.
2. Start tracking with the button at the top right ("Start sharing" or "Schematic: ...").
3. Decide who takes what in the right-hand column of each row. "Tools" at the top has bulk assigning, stats, history and more.

## For server owners

`config/litematica_materialshare_server.json` is created on the first start.

| Key | Meaning |
|---|---|
| `maxProjects` | How many schematics can be shared at once (1-64). Default 64 |

Data is stored in the world. Everybody with the mod sees all schematics and can edit them (every action is in the history).

## Compatibility

Litematica / malilib screens are only extended with mixin `@Inject`s; nothing of other mods is overwritten. Tested together with Sodium, Sodium Extra and Reese's Sodium Options. Buttons do not overlap at any GUI scale.

## ライセンスとリンク / License and links

- ライセンス: All Rights Reserved(LICENSE を見てください)/ License: All Rights Reserved (see LICENSE)
- Modrinth: https://modrinth.com/mod/litematica-materialshare
- バグ報告・要望 / Issues: https://github.com/zyorokia926/litematica-MaterialShare/issues
- Litematica の作者とは関係のない、非公式のアドオンです。/ An unofficial add-on, not affiliated with the authors of Litematica.

## ビルド / Build

ビルドには、`libs/` に Litematica 0.14.7、malilib 0.15.4、Syncmatica、Mod Menu の jar を置く必要があります(このリポジトリには含めていません。`build.gradle` を見てください)。
Building needs the jars of Litematica 0.14.7, malilib 0.15.4, Syncmatica and Mod Menu in `libs/` (they are not part of this repository; see `build.gradle`).
