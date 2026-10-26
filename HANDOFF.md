# 引き継ぎメモ(新しい会話で、続きの作業をするための資料)

新しい会話の最初に、次のように頼んでください。

> `HANDOFF.md` と `CLAUDE.md` を読んで、続きの作業を再開して。まず、今の状態を5行でまとめて。

## 1. このプロジェクト

- **Litematica MaterialShare**: Litematica のマテリアルリストに、「誰が、どの素材を、いくつ集めるか」を表示して、サーバーの全員で共有する Fabric 用のアドオンです(日本語と英語の画面)。
- MOD id: `litematica_materialshare`、パッケージ: `dev.materialshare`、MODのver: 0.10.0、通信のver(`ProtocolCheck.PROTOCOL_VERSION`): 16。
- ライセンス: All Rights Reserved(転載、改造、再配布を許可していない)。作者: zyorokia926。
- 公開先: Modrinth(https://modrinth.com/mod/litematica-materialshare)、GitHub(https://github.com/zyorokia926/litematica-materialshare)。
- 主な機能: 担当の割り当て、人ごとの数、一括割り当て、足りない素材、HUD(ドラッグで位置を決める)、統計と履歴、保管場所(範囲を登録して、チェストなどの中身を数える、納品を自動記録する、3Dの枠、シュルカーボックスの中も数える)、原材料の分解図、使い方の画面(写真つき)。

## 2. 対応しているMinecraftのver(14個)

1.18、1.18.1、1.18.2、1.19、1.19.1、1.19.2、1.19.3、**1.19.4(メインのビルド)**、1.20.1、1.20.2、1.20.3、1.20.4、1.20.5、1.20.6。

- 1.20 そのものは作っていません(Litematica に 1.20 用の build が無いため)。
- 1つのverごとに jar が別です。名前は `litematica-materialshare-0.10.0+<ver>.jar`。1.19.4 だけ `litematica-materialshare-0.10.0.jar` です。
- 前提のMOD(Litematica、malilib、Syncmatica、Fabric API)が1つでも、そのverに無ければ、そのverは作らない方針です。
- 必要なものの表は `README.md` にあります。Java 17(1.20.5 以降は Java 21)。Fabric API の最低ver: 1.18/1.18.1=0.44.0、1.19/1.19.1=0.58.0、1.19.2〜1.19.4=0.76.0、1.20.1=0.92.2、1.20.2=0.91.6、1.20.3=0.91.1、1.20.4=0.97.2、1.20.5=0.97.8、1.20.6=0.100.8。

## 3. コードの作り

- ソースは全verで共通です(`src/main`)。verごとに違うゲームのAPIは、`src/compat/<ver>/java/dev/materialshare/` の `McCompat`(サーバー側、verの範囲)、`McClientCompat`(クライアント側、2D描画)、`McNet` / `McClientNet`(通信)に閉じ込めてあります。共通のコードでは、ゲームのver固有のクラスを直接使いません。
- 1.20 以降は、画面が `MatrixStack` ではなく `DrawContext` を受け取ります。`ports/build.py` が、型を置き換えます。1.20.2 以降の `onMouseScrolled` の引数の追加や、1.20.5 以降の `writeNbt` の引数の追加も、`ports/build.py` が置き換えます。
- 1.20.5 以降は、通信が「型つきの payload」になりました。`McNet` が、全チャンネルを「バイト列を運ぶ payload」として登録し、それ以外のコードは、今までどおり、チャンネル名と `PacketByteBuf` を使います。
- 1.20.5 以降のビルドは、Loom 1.14.10、Gradle 9.2、Java 21 が必要です(`ports/build.py` が設定します。Gradle は `ports/setup-gradle9.sh` で `/opt/gradle9` に入れます)。
- 参加直後に、サーバーから送ったデータが捨てられる問題(Fabric API 0.91.1 の 1.20.3 で発生)の対策として、`ServerNet` に `PENDING_SYNC` があります。チャンネルが分かった時点で、もう一度送ります。

## 4. ビルドとテストの手順

- ビルド: `python3 ports/build.py <ver>`(Modrinth から前提MODを取得して、`dist/` に jar を置く)。メイン(1.19.4)は `gradle build`。
- 2人テスト: `ports/test/two-clients.sh <ver>`(サーバー1つ、クライアント2人)。複数のverを2つずつ同時に試すのは `ports/test/test-parallel.sh <ver> <ver> ...`。結果は `/tmp/materialshare-test-logs/summary.txt` で、合格は「ok-lines: 3 last-lines: 2」です。
- テストのデータは `ports/test/data/` にあります。プログラムは `ports/test/TwoClientProbe.java.txt`(`TmpProbe.java` としてコピーして使い、コミットしません)。
- テスト環境は Linux 専用です(xvfb-run、/proc を使う)。Windows や Mac で動かすには、書き直しが必要です。
- 1つのverの2人テストは、約1.5分です(4コア)。2つ同時だと約15%短縮。
- **mixin が適用されなかった場合、テストは `MIXIN FAILED` と表示します。** テストが通っても、これが出ていれば不合格です。

## 5. 見つけた落とし穴

- `pkill -f <名前>` は、コマンド自身の文字列にも一致して、自分のシェルを殺すことがあります。`pgrep` で PID を取り、自分を除いて止めてください。
- 1つの作業フォルダに対して、複数の Gradle を同時に動かすと、コンパイルが衝突することがあります。先に `gradle classes processResources` を1回実行してから起動します。
- テストの確認プログラムは、サーバーが起動する前につなぐと諦めます。今は、再接続するようにしてあります。
- Litematica の設計図の配置データは、サーバーのアドレスとポート(`127.0.0.1_25565`)が名前に入っています。ポートを変えるときは、ファイル名も変えます(スクリプトが行います)。
- 設定項目の文言のキーは、MODの名前を付けません(`config.name.<小文字>`)。`LangFilesTest` は、動的に文字列を連結したキーを禁止しています。
- `HelpScreen.REVIEWED_PROTOCOL` は、通信のverを上げたら、見直したうえで上げます(`HelpPagesTest`)。
- 2人テストの確認プログラムは、mixin の失敗や、通信路の報告の遅れを検出しません。ログも見てください。

## 6. GitHub

- jar は GitHub に入れていません(`dist/` は手元だけ)。
- 日本語では、バージョンを「ver」と書きます(GitHub の文書、Issue、コメント、MODの中、Modrinth の説明文、手元のメモ、すべて同じ。2026-10-26 にオーナーが決定。「日本語版/英語版」はそのまま)。
- Issue: #1(1.19.x の他のverに対応する)は完了して閉じました。#2(1.21 以上に対応する)と、#3(保管場所の機能を広げる)は開いています。
- 私(Claude)の環境から、About の説明、トピック、Releases は変更できません。オーナーが画面で行います。

## 7. 残っている作業(オーナー側)

- Modrinth に、1.20.1〜1.20.6 の jar を、1つずつ登録する(1.19.4 以前は登録済み)。
- Modrinth の説明文を、`dist/modrinth/description_both.md`(対応表あり)に差し替える。
- GitHub の About の説明を「Unofficial Litematica add-on: share who collects which material, and how many, with everybody on the server (Fabric, Minecraft 1.18 to 1.20.6).」に変え、トピックの `fabric-mo` を `fabric-mod` に直す。
- Modrinth の個人用アクセストークンと、作業環境に追加したネットワークのシークレットを、削除する(使わなくなった)。

## 8. 未決定のこと

- ソースを、ver の系統(1.19系 / 1.20系)ごとに分けるか。今は共通で、触る場所に応じて、テストする系統を決める(オーナーの許可なしに分けない)。
- サーバーの `depends` から、malilib と Syncmatica を外すか(今は、サーバーも、どちらも必要)。
- 1.21 以降に対応するか(#2)。先に、実現できるかと作業量を調べる。画面の仕組みが大きく変わる見込み。
- 保管場所の拡張(エンダーチェストなど)は、#3 で検討中。大釜は対象外と決定済み。

## 9. テストをしていないこと

- 1.19.4 以前のverは、1.20系に対応するために共通のコードを変えたあと、動作の再テストをしていません(ビルドは通っています)。
- 2人が同じ値を同時に変える競合は、試していません。
- 1.18.1 は、途中のテストで1回だけ失敗しました(チェストを開けられなかった)。再現していません。

## 10. オーナーとの進め方

- 日本語で、結論を先に、短く書く。確認できたこと、できていないことを分ける。
- 機能の実装は、オーナーの「始めてください」を待つ。公開、削除、外部への送信は、毎回確認する。
- 作業は効率よく、トークンとセッションを無駄にしない。長い作業は裏で1回だけ実行して待つ(繰り返し確認しない)。
- テストは、オーナーが指定したverだけ。
- 新しいverを頼まれたら、前提MODの有無を先に調べ、欠けているverは作らず、報告する。2人以上のクライアントで必ず試す。
- 変えたことが GitHub に関係するなら、必ず GitHub にも書く。
