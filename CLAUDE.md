# Litematica MaterialShare: notes for working on this repository

## The how-to screen must always match the mod

`HelpScreen` (client) is the how-to for players: one page per feature, with pictures of the real screens, in Japanese and
English. **Whenever a feature is added, changed or removed, update the how-to in the same change:**

1. The page list is `HelpScreen.PAGES` (page id + picture names). Add, rename or remove pages there.
2. Texts: `help.<id>.title`, `help.<id>.short` (one line for the contents list) and `help.<id>.body` in both
   `ja_jp.json` and `en_us.json`. Describe what the player sees and presses, using the exact button labels of the
   language files (so look them up, do not remember them).
3. Pictures: `src/main/resources/assets/litematica_materialshare/textures/gui/help/{ja,en}/<name>.png`, about 800 px wide,
   taken from the real game (run a client against a server with a seeded project, once per language; screens of
   several people are more useful than empty ones). Retake a picture whenever its screen looks different.
4. Raise `HelpScreen.REVIEWED_PROTOCOL` after reviewing. `HelpPagesTest` fails when the protocol version changed
   without that, when a page lacks a text in a language, or when a picture is missing.
5. Mention new hotkeys and settings in the "settings" page, new chat commands in the "commands" page, new tools in
   the "tools" page.

Also keep `README.md`, `dist/modrinth/description_*.md` and `dist/modrinth/changelog.md` in step.

## Minecraft versions

The main build is for 1.19.4. Other versions (1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.20.1 to 1.20.6; there is no 1.20 because Litematica has no build for it) are built from the same source with `python3 ports/build.py <version>`
(downloads the matching Litematica, malilib, Syncmatica and Mod Menu from Modrinth, builds, and puts the jar into `dist/`).
Everything that differs in the game's API sits in `src/compat/<version>/java/dev/materialshare/McCompat.java` (server side
and version ranges) and `McClientCompat.java` (client side). Do not use version-specific game classes (or `Text.literal`, the Fabric client command API, resources, the Fabric networking API (`McNet`/`McClientNet`), 2D drawing (`McClientCompat.fill/text/texture/tooltip`; from 1.20 the screens get a `DrawContext` instead of a `MatrixStack`, `ports/build.py` swaps the type), item contents (`McCompat.forEachInside`)) anywhere else; add a
method to the compat files of all versions instead. After a change, build and try every version (`ports/build.py`, then a server with two clients).
**For every new Minecraft version the owner asks for, test with two or more clients on one server** (at least: both assign
the same item, change counts, one hands items into a storage chest while the other watches the numbers). A single client
is not enough. `ports/test/` has the probe (`TwoClientProbe.java.txt`, copied in as `TmpProbe.java` and never committed to src) and
the script (`two-clients.sh <version>`, needs the work folder that `ports/build.py` made). Set `spawn-protection=0` on the test
server and teleport the second player next to the chest, or he cannot open it. All builds (1.18 to 1.20.6) passed it. The script also prints `MIXIN FAILED` for any mixin that did not apply; a passing probe is not enough without that check. 1.20.5 and later need Gradle 9.2 (Loom 1.14, set in `ports/build.py`; the script uses `/opt/gradle9`) and Java 21. Before releasing, also check that every prerequisite mod (Litematica, malilib, Syncmatica,
Fabric API) exists for the version and that the Litematica build asks for the malilib build you chose (1.18 needs malilib 0.10.x).

## Other rules

- Bump `ProtocolCheck.PROTOCOL_VERSION` whenever packets change (and describe the change in its comment).
- Every text the code uses must exist in both language files (`LangFilesTest`).
- Buttons must never overlap or overflow at any GUI scale (check new screens at several window sizes).
- Do not start implementing a feature the owner has only discussed; wait for their go-ahead.
- On GitHub (README, the docs pushed there, issues and comments) write "ver" instead of "版" for a version, in Japanese. The mod text, the Modrinth descriptions and the other files keep "版". "日本語版/英語版" stays as it is.
- Test only the versions the owner names (for example only 1.20 to 1.20.6 when those were asked for). Say which versions were not tested again after a change of the shared code. Run long jobs once in the background and wait for them; do not poll in a loop (it wastes the session).
- The source is shared by all versions; only `src/compat/<version>/` differs. Splitting it per version family (1.19 line / 1.20 line) was discussed and not decided; do not do it without the owner's go-ahead.

---

# 日本語(上の内容と同じです)

# Litematica MaterialShare: このリポジトリで作業するときのメモ

## 使い方の画面は、いつもMODと一致させる

`HelpScreen`(クライアント)は、プレイヤー向けの使い方です。機能ごとに1ページあり、実際の画面の写真つきで、日本語と英語があります。
**機能を足す、変える、消すときは、同じ変更の中で使い方も更新します。**

1. ページの一覧は `HelpScreen.PAGES`(ページ id と写真の名前)です。ページの追加、名前の変更、削除はここで行います。
2. 文言は `help.<id>.title`、`help.<id>.short`(目次の1行)、`help.<id>.body` を、`ja_jp.json` と `en_us.json` の両方に書きます。
   プレイヤーに見えるものと押すものを書き、ボタンの名前は言語ファイルの文字そのままにします(記憶で書かず、必ず調べます)。
3. 写真は `src/main/resources/assets/litematica_materialshare/textures/gui/help/{ja,en}/<名前>.png` です。幅は約800px、
   実際のゲームで撮ります(見本の設計図を入れたサーバーにクライアントをつなぎ、言語ごとに1回)。複数の人が出る画面のほうが
   役に立ちます。画面の見た目が変わったら、その写真を撮り直します。
4. 見直したら `HelpScreen.REVIEWED_PROTOCOL` を上げます。`HelpPagesTest` は、通信のverが変わったのにこれを上げていないとき、
   ページの文言が片方の言語にないとき、写真がないときに失敗します。
5. 新しいホットキーと設定は「設定」ページ、新しいチャットコマンドは「コマンド」ページ、新しいツールは「ツール」ページに書きます。

`README.md`、`dist/modrinth/description_*.md`、`dist/modrinth/changelog.md` も、同じ変更の中でそろえます。

## Minecraft のver

メインのビルドは 1.19.4 です。そのほかのver(1.18、1.18.1、1.18.2、1.19、1.19.1、1.19.2、1.19.3、1.20.1〜1.20.6。
Litematica に 1.20 用の build が無いので、1.20 はありません)は、同じソースから `python3 ports/build.py <ver>` で作ります
(合うLitematica、malilib、Syncmatica、Mod Menu を Modrinth から取得して、ビルドし、jar を `dist/` に置きます)。
ゲームのAPIの違いは、すべて `src/compat/<ver>/java/dev/materialshare/` の `McCompat.java`(サーバー側とverの範囲)、
`McClientCompat.java`(クライアント側)、`McNet.java` / `McClientNet.java`(通信)に閉じ込めます。
ver固有のゲームのクラス(`Text.literal`、Fabric のクライアントコマンドAPI、リソース、通信、2D描画、アイテムの中身など)を、
それ以外の場所で使わないでください。必要なら、全verの compat に同じメソッドを足します。
1.20 からは画面が `MatrixStack` ではなく `DrawContext` を受け取るので、`ports/build.py` が型を置き換えます。
変更したら、全verをビルドして、サーバー1つ+クライアント2人で試します。
**オーナーが新しいverを頼んだときは、必ず、1つのサーバーに2人以上のクライアントでつないで試します。**
(最低限: 2人が同じ素材を担当にする、数を変える、片方が保管場所のチェストに納品している間にもう片方が数字を見る)。
1人のクライアントだけでは足りません。`ports/test/` に、試験用のプログラム(`TwoClientProbe.java.txt`。`TmpProbe.java` として
コピーして使い、src にはコミットしません)とスクリプト(`two-clients.sh <ver>`。`ports/build.py` が作った作業フォルダが必要)が
あります。試験用サーバーでは `spawn-protection=0` にし、2人目をチェストのそばへ移動させてください(そうしないと開けられません)。
スクリプトは、適用されなかった mixin があると `MIXIN FAILED` と表示します。試験が通っても、この確認がないと不十分です。
1.20.5 以降は Gradle 9.2(Loom 1.14。`ports/build.py` で設定。スクリプトは `/opt/gradle9` を使う)と Java 21 が必要です。
公開の前に、前提のMOD(Litematica、malilib、Syncmatica、Fabric API)がそのverにあること、選んだ malilib を Litematica が
要求していることも確認します(1.18 は malilib 0.10.x が必要です)。

## そのほかのルール

- 通信(パケット)を変えたら `ProtocolCheck.PROTOCOL_VERSION` を上げます(変更の内容を、そのコメントに書きます)。
- コードが使う文言は、2つの言語ファイルの両方に必要です(`LangFilesTest`)。
- ボタンは、どのGUIの大きさでも、重ならない、はみ出さないようにします(新しい画面は、複数のウィンドウの大きさで確認します)。
- オーナーが話し合っただけの機能は、実装を始めません。オーナーの「始めてください」を待ちます。
- GitHub(README、GitHub に送る文書、Issue、コメント)の日本語では、バージョンを「版」ではなく「ver」と書きます。
  MODの中の文言、Modrinth の説明文、そのほかのファイルは「版」のままです。「日本語版/英語版」はそのままです。
- テストは、オーナーが指定したverだけにします(たとえば 1.20〜1.20.6 を頼まれたら、その範囲だけ)。共通のコードを変えたあと、
  再テストしていないverがあれば、そう伝えます。長い作業は、裏で1回だけ実行して待ちます。ループで何度も確認しない(セッションの無駄になります)。
- ソースは全verで共通で、違うのは `src/compat/<ver>/` だけです。verの系統(1.19系 / 1.20系)ごとにソースを分ける案は、
  話し合っただけで、決まっていません。オーナーの「始めてください」なしに、分けないでください。
