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

## Other rules

- Bump `ProtocolCheck.PROTOCOL_VERSION` whenever packets change (and describe the change in its comment).
- Every text the code uses must exist in both language files (`LangFilesTest`).
- Buttons must never overlap or overflow at any GUI scale (check new screens at several window sizes).
- Do not start implementing a feature the owner has only discussed; wait for their go-ahead.
