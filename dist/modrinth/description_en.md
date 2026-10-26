# Litematica MaterialShare

**An add-on for Litematica that shows who collects which material, and how much, to everybody on the server.**
(Fabric / Minecraft 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6)

*Unofficial add-on, not affiliated with the authors of Litematica.*

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
| 1.20.1 | 0.15.x (tested: 0.15.4) | 0.16.x (tested: 0.16.3) | `...-0.10.0+1.20.1.jar` |
| 1.20.2 | 0.16.x (tested: 0.16.1) | 0.17.x (tested: 0.17.0) | `...-0.10.0+1.20.2.jar` |
| 1.20.3 | 0.17.x (tested: 0.17.4) | 0.18.x (tested: 0.18.3) | `...-0.10.0+1.20.3.jar` |
| 1.20.4 | 0.17.x (tested: 0.17.4) | 0.18.x (tested: 0.18.3) | `...-0.10.0+1.20.4.jar` |
| 1.20.5 | 0.18.x (tested: 0.18.3) | 0.19.x (tested: 0.19.2) | `...-0.10.0+1.20.5.jar` |
| 1.20.6 | 0.18.x (tested: 0.18.3) | 0.19.x (tested: 0.19.2) | `...-0.10.0+1.20.6.jar` |

Syncmatica: any version. Fabric API: 0.76.0 or newer (1.19 and 1.19.1: 0.58.0 or newer; 1.18 and 1.18.1: 0.44.0 or newer).

Fabric API for 1.20.x: 1.20.1 0.92.2 or newer, 1.20.2 0.91.6, 1.20.3 0.91.1, 1.20.4 0.97.2, 1.20.5 0.97.8, 1.20.6 0.100.8 (each or newer). Minecraft 1.20.5 and 1.20.6 need Java 21. There is no jar for Minecraft 1.20 itself, because Litematica has no build for it (use 1.20.1).

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
