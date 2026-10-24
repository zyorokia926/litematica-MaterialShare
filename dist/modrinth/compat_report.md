# 他MODとの互換性検証まとめ

対象: Litematica MaterialShare 0.10.0 / Minecraft 1.19.4 / Fabric

## 結論

- **私のmodが原因の競合は、検証した範囲で 0 件でした。**
- サーバー側: サーバーで使える 80 件のうち、80 件が、私のmodと一緒に起動しました。
- クライアント側: 実際にサーバーへ参加し、材料リスト・プロジェクト・ツール・足りない素材の画面を開いて確認できたのは 34 件です。確認前に中断したのは 21 件です。
- 失敗した MOD は、すべて「私のmodを外しても同じ理由で失敗する」ものでした（MOD側が新しい Fabric Loader や前提MODを必要とする）。

## 検証方法

- サーバー: 本物の Fabric サーバー（Loader 0.14.21。失敗したものは 0.20.1 で再確認）に、Fabric API・malilib・Syncmatica・私のmod・対象MODを入れ、起動を確認。失敗時は私のmodを外した対照でも試験。
- クライアント: 開発環境の Minecraft に Litematica・malilib・Syncmatica・私のmod・対象MODを入れ、サーバーに参加して画面を操作（Fabric Loader 0.20.1）。MODは 9 件前後ずつ組み合わせて試験。
- 前提のMODが必要なものは、Modrinth の情報から自動で追加。

## 失敗・要注意（私のmod以外が原因）

| MOD | 状況 |
|---|---|
| Memory Leak Fix | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| Ledger | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| Day Dream | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| Axes Are Weapons | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| Neutral Animals | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| StitchedSnow | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。前提MOD(Cloth Config)を足すと、正常起動 |
| Mobs Attempt Parkour | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| Distant Horizons | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| Just Enough Items | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |
| e4mc | Loader 0.14.21 では起動せず(MOD側が新しいLoaderを要求)。Loader 0.20.1 で、私のmodと一緒に正常起動 |

## MODごとの結果

○ = 私のmodと一緒に正常 / × = 失敗 / 未確認 = 中断のため未試験

| MOD | 試験した版 | サーバー | クライアント |
|---|---|---|---|
| Lithium | mc1.19.4-0.11.1 | ○ | ○ |
| FerriteCore | 5.2.0-fabric | ○ | ○ |
| ModernFix | 5.7.2+mc1.19.4 | ○ | ○ |
| Krypton | 0.2.2 | ○ | 未確認 |
| C2ME | 0.2.0+alpha.10.66+1.19.4 | ○ | ○ |
| ServerCore | 1.3.5+1.19.4 | ○ | 対象外(サーバー専用) |
| Alternate Current | mc1.19-1.9.0 | ○ | 対象外(サーバー専用) |
| Chunky | 1.3.74 | ○ | ○ |
| VMP (Very Many Players) | 0.2.0+beta.7.73+1.19.4 | ○ | ○ |
| Fastload | 3.4.0 | ○ | 未確認 |
| Ksyxis | 1.4.6 | ○ | 対象外(サーバー専用) |
| Phosphor | mc1.19.x-0.8.1 | ○ | ○ |
| Starlight | 1.1.1+1.19 | ○ | ○ |
| LazyDFU | 0.1.3 | ○ | ○ |
| Memory Leak Fix | v1.1.5 | ○ (Loader 0.20.1) | ○ |
| Spark | 1.10.37-fabric | ○ | ○ |
| Ledger | 1.2.7 | ○ (Loader 0.20.1) | 対象外(サーバー専用) |
| Carpet | 1.4.101 | ○ | ○ |
| MixinTrace | 1.1.1+1.17 | ○ | ○ |
| TellMe | 0.9.1 | ○ | ○ |
| LuckPerms | v5.4.66-fabric | ○ | 対象外(サーバー専用) |
| Player Roles | v1.6.4+1.19.4 | ○ | 対象外(サーバー専用) |
| BanHammer | 0.6.4+1.19.3 | ○ | 対象外(サーバー専用) |
| Vanish | 1.4.1+1.19.4 | ○ | 対象外(サーバー専用) |
| GolfIV | 0.6.3 | ○ | 対象外(サーバー専用) |
| Textile Backup | 3.1.2-1.19 | ○ | 対象外(サーバー専用) |
| DynRes | 1.2.9 | ○ | 対象外(サーバー専用) |
| JLine for Dedicated Server | 0.7.0 | ○ | 対象外(サーバー専用) |
| Fabric Console | 1.1.4 | ○ | 対象外(サーバー専用) |
| Styled Chat | 2.1.7+1.19.4 | ○ | 対象外(サーバー専用) |
| Chat+ | 1.1.1-mc1.19.3 | ○ | 対象外(サーバー専用) |
| SimpleChat | 1.8.2 | ○ | 対象外(サーバー専用) |
| Chat Heads | 0.15.6 | 対象外(クライアント専用) | ○ |
| No Chat Reports | Fabric-1.19.4-v2.1.6 | ○ | ○ |
| MiniMOTD | 2.0.12 | ○ | 対象外(サーバー専用) |
| Styled Player List | 3.0.0+1.19.4 | ○ | 対象外(サーバー専用) |
| TabTPS | 1.3.17 | ○ | 対象外(サーバー専用) |
| Playtime Tracker | 2023.0611+mod | ○ | ○ |
| BlueMap | 5.3-fabric-1.19.4 | ○ | 対象外(サーバー専用) |
| Dynmap | 3.6 | ○ | ○ |
| Essential Commands | 0.34.0-mc1.19.4 | ○ | 対象外(サーバー専用) |
| The Warp Mod | 0.0.4 | ○ | ○ |
| Armor Stand Editor | 2.1.0+1.19.4-rc2 | ○ | 対象外(サーバー専用) |
| WorldEdit | 7.2.14 | ○ | ○ |
| Inventory Sorting | 1.8.10-1.19.4 | ○ | ○ |
| Inv View | 1.4.10 | ○ | 対象外(サーバー専用) |
| Couplings | 1.9.3+1.19.4 | ○ | ○ |
| EditSign | 1.19.4-2.8.0 | 対象外(クライアント専用) | ○ |
| EasyAutoCrafting | 1.7+1.19.4 | ○ | 対象外(サーバー専用) |
| BetterSleeping | 0.6.2+1.19 | ○ | 対象外(サーバー専用) |
| Day Dream | 1.0.7 | ○ (Loader 0.20.1) | 対象外(サーバー専用) |
| Leaf Me Alone | 1.0.1 | ○ | ○ |
| Falling Tree | 3.13.1 | ○ | ○ |
| Monsters in the Closet | v1.0.3+1.19 | ○ | ○ |
| Villager Names | 1.19.4-5.2-forge+fabric | ○ | ○ |
| Villager See, Villager Do | 1.1.9+MC1.19.4 | ○ | 対象外(サーバー専用) |
| Economical Villager Trading | 1.2.9+MC1.19.3-1.19.4 | ○ | 対象外(サーバー専用) |
| Better Respawn | fabric-1.19.4-2.0.2 | ○ | 対象外(サーバー専用) |
| Fabrication | 3.5.8+1.19.4 | ○ | ○ |
| Mini Tweaks | 1.3.6 | ○ | 対象外(サーバー専用) |
| Axes Are Weapons | 1.10.2-fabric-1.20.2 | ○ (Loader 0.20.1) | 対象外(サーバー専用) |
| Neutral Animals | 1.2.3 | ○ (Loader 0.20.1) | 対象外(サーバー専用) |
| Snowy | 2.2.1+1.19.4-fabric | ○ | 対象外(サーバー専用) |
| StitchedSnow | 1.0.6 | ○ (Cloth Config を追加) | 対象外(サーバー専用) |
| Cauldron Concrete | 1.0.0b+mod | ○ | ○ |
| Unforgiving Void | 3.1.7+1.19.4+fabric | ○ | 対象外(サーバー専用) |
| Mobs Attempt Parkour | 0.4-1.19.4 | ○ (Loader 0.20.1) | 対象外(サーバー専用) |
| Wandering Collector | 1.2.1+mc1.19.4 | ○ | 対象外(サーバー専用) |
| Sodium | mc1.19.4-0.4.10 | 対象外(クライアント専用) | ○ |
| Iris Shaders | 1.6.11+1.19.4 | 対象外(クライアント専用) | ○ |
| Entity Culling | 1.11.3 | 対象外(クライアント専用) | ○ |
| ImmediatelyFast | 1.2.11+1.19.4-fabric | 対象外(クライアント専用) | ○ |
| Mod Menu | 6.3.1 | 対象外(クライアント専用) | ○ |
| Dynamic FPS | 3.11.4 | 対象外(クライアント専用) | 未確認 |
| Distant Horizons | 3.3.3-1.19.4 | ○ (Loader 0.20.1) | 未確認 |
| AppleSkin | 2.5.1+mc1.19.4 | ○ | 未確認 |
| Jade | 10.4.0 | ○ | 未確認 |
| Chat Patches | 194.5.1 | 対象外(クライアント専用) | 未確認 |
| First-person Model | 2.7.3 | 対象外(クライアント専用) | 未確認 |
| Xaero's Minimap | 25.2.10_Fabric_1.19.4 | ○ | 未確認 |
| Xaero's World Map | 1.39.12_Fabric_1.19.4 | ○ | 未確認 |
| Just Enough Items | 13.1.0.19 | ○ (Loader 0.20.1) | 未確認 |
| Simple Voice Chat | fabric-1.19.4-2.5.12 | ○ | 未確認 |
| Ping Wheel | 1.12.1 | ○ | 未確認 |
| Emotecraft | 2.2.7-b.build.50+1.19.4-fabric | ○ | 未確認 |
| e4mc | 6.2.3-fabric | ○ (Loader 0.20.1) | 未確認 |
| Essential Mod | 1.4.1.1 | 対象外(クライアント専用) | 未確認 |
| Nature's Compass | 1.19.4-2.2.0-fabric | ○ | 未確認 |
| Explorer's Compass | 1.19.4-2.2.1-fabric | ○ | 未確認 |
| Death Finder | v6.0.0-1.19.4-Fabric | ○ | 未確認 |
| Inventory Profiles Next | fabric-1.19.4-1.10.6 | 対象外(クライアント専用) | 未確認 |
| Waystones | 13.1.0+fabric-1.19.4 | ○ | 未確認 |

## 試験できなかったMOD

### 1.19.4 向けの Fabric 版が見つからなかったもの（78 件）

名前: Noisium、ScalableLux、Structure Layout Optimizer、Hydrogen、World Specific View Distance、Recipe Cache、Fast Furnace for Fabric、DeltaLogger、CommandSpy、Erroring Entity Remover、Anonymize、TeamPerms、Better Op、SimpleAuth、Gatekeeper、Simple Anti X-ray、CheaterDeleter、Badlion Client Disabler、Auto Backup、Minesave、Fabric Restart、server-launcher and restarter、ModUpdater、Shut Up Console、Translated Server Log、Fabric Death Messages、Random Server Icon、TabPing、Fabric-Votifier、Discord-Fabric Link、Interweave、DisFabric、DiscordMinecraftFabric、fabric-discord-integration、Discord Fabric Whitelister、BlockBot、MatterLink、TomsServerUtils、TinyMap、FabricHomes、FabricTPA、FabricWarps、GlobalSpawn、Default Spawn Points、More Commands Mod、Command Alias Creator、Slapmap、Taterzens、Shapes Edit、SimpleServerSorter、Lootstrap、InvSync、Configured Keep Inventory、keepinventory-command、SimpleHarvest、Better Signs & Frames、Fat Experience Orbs、Hypnos、BetterSleepPlus、SleepPunisher、Leaf Decay、Fabric Tree Chopper、Trees Do Not Float、Infinite Water Bucket、Compostable Rotten Flesh、Farmable Shulkers、InfiniteVillagerTrading、FabricSit、Home Camp、Fire's Survival Tweaks、PvP toggle、AttackSpeedPlus、Lightning Prevention、Less Annoying Piglins、Stop Strider Suffering、Endermen are Friends、Neutral Mobs、Chat Animation

（Modrinth の検索で見つからなかったか、1.19.4 向けの版が無いものです。名前が違う場合もあります。）

### 中断のため、クライアントで未確認のもの

Krypton、Fastload、Dynamic FPS、Distant Horizons、AppleSkin、Jade、Chat Patches、First-person Model、Xaero's Minimap、Xaero's World Map、Just Enough Items、Simple Voice Chat、Ping Wheel、Emotecraft、e4mc、Essential Mod、Nature's Compass、Explorer's Compass、Death Finder、Inventory Profiles Next、Waystones

## 注意

- 複数のMODを組み合わせた試験のため、MOD同士の相性は個別には見ていません。私のmodが原因かどうかは、ログと、画面操作の結果で判断しました。
- Sodium、Sodium Extra、Reese's Sodium Options の組み合わせは、別の試験で、画面サイズごとのボタンの重なりまで確認済みです。