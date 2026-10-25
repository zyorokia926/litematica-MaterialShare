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

The main build is for 1.19.4. Other versions (1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3) are built from the same source with `python3 ports/build.py <version>`
(downloads the matching Litematica, malilib, Syncmatica and Mod Menu from Modrinth, builds, and puts the jar into `dist/`).
Everything that differs in the game's API sits in `src/compat/<version>/java/dev/materialshare/McCompat.java` (server side
and version ranges) and `McClientCompat.java` (client side). Do not use version-specific game classes (or `Text.literal`, the Fabric client command API, resources) anywhere else; add a
method to both compat files instead. After a change, build and try every version (`ports/build.py`, then a server with two clients).
**For every new Minecraft version the owner asks for, test with two or more clients on one server** (at least: both assign
the same item, change counts, one hands items into a storage chest while the other watches the numbers). A single client
is not enough. `ports/test/` has the probe (`TwoClientProbe.java.txt`, copied in as `TmpProbe.java` and never committed to src) and
the script (`two-clients.sh <version>`, needs the work folder that `ports/build.py` made). Set `spawn-protection=0` on the test
server and teleport the second player next to the chest, or he cannot open it. All builds (1.18 to 1.19.4) passed it. Before releasing, also check that every prerequisite mod (Litematica, malilib, Syncmatica,
Fabric API) exists for the version and that the Litematica build asks for the malilib build you chose (1.18 needs malilib 0.10.x).

## Other rules

- Bump `ProtocolCheck.PROTOCOL_VERSION` whenever packets change (and describe the change in its comment).
- Every text the code uses must exist in both language files (`LangFilesTest`).
- Buttons must never overlap or overflow at any GUI scale (check new screens at several window sizes).
- Do not start implementing a feature the owner has only discussed; wait for their go-ahead.
