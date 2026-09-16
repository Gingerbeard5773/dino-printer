<div align="center">
  <img src="/src/main/resources/assets/dinoprinter/icon.png" alt="logo" width="20%"/>
  <h1>
    Dino Printer<br>
    <sub>by dinosaurwizard</sub>
  </h1>

  <p>A robust printer for Litematica schematics.</p>
</div>

<hr />

# Overview

**Dino Printer** is an all-purpose 'just works' schematic printer designed for servers with strict anti-cheat systems (or not!).

It is built from scratch and is **not a fork of any other Meteor printer**.

The overarching goal of Dino Printer is to provide reliable schematic printing while still supporting advanced features that are difficult to implement on stricter servers.

# How It Works

Dino Printer uses a dynamic system to determine **how each block should be placed**.

Instead of simply assuming how a block should be placed, Dino Printer **simulates the placement at each possible point** and examines the `BlockState` to see if it matches the schematic.

This makes the system highly flexible and allows it to work with **nearly all Minecraft blocks**.

# Features

* **Advanced BlockState Matching** — Simulates placement to ensure every block matches the schematic's required state.
* **Superior Raytracing** — Searches for multiple points across block surfaces instead of relying on a single point. This allows for reliable placement on servers that require raytracing.
* **Hack Rotation** — Allows rotatable blocks to be placed in their correct orientation from anywhere. Works on most servers!
* **Anti-Cheat Support** — Flexible placement settings let you adapt to your server's restrictions.

# Installation
- Download the relevant [release](/../../releases) of the mod from the releases tab.
- Put the .jar in your `.minecraft/mods` folder where you have installed Meteor.
- This addon requires [Meteor Client](https://meteorclient.com/).

### Dependencies
Use the fork of Litematica maintained by [sakura-ryoko](https://github.com/sakura-ryoko)

- [Litematica](https://github.com/sakura-ryoko/litematica)
- [Malilib](https://github.com/sakura-ryoko/malilib)

# Configuration

Dino Printer offers a large amount of settings that may require configuring for each server.
Here is an in-depth explanation of each relevant setting and how you should use them.

| Setting | Description | Notes |
| :--- | :--- | :--- |
| **Place Delay** | The tick delay between placing blocks. | Lower values place faster. |
| **Place Retry Delay** | Delay in ticks before retrying a failed placement. | Keep this setting somewhat high, as you don't want to waste time on failed place positions. |
| **Place Range** | Maximum distance from the player at which blocks can be placed. | Server dependent. |
| **Blocks Per Tick** | Maximum number of blocks placed per tick. | Most servers do not allow more than 1 block per tick. |
| **Wall Place** | Allows placement through walls. | Disable this if your server requires ray-tracing. |
| **Air Place** | Allows blocks to be placed without using another block-face. | This is a hack so some servers may disallow/limit it. |
| **Sneak Place** | Sends sneaking packets when placing blocks. | Use this if you are placing against interactable blocks like furnaces or chests. |
| **Rotate** | Rotates toward blocks before placing them. | Required for servers that dislike it when you don't rotate towards your placements. |
| **Rotation Place** | Respects block rotation when placing. | This hack lets you rotate blocks from any direction. **NOTE: IT DOESNT WORK FULLY IN SINGLEPLAYER** |
| **Strict Rotation** | Only allows rotation placements that could be performed legitimately. | Use this if your server doesn't allow the Rotation-Place hack. |
| **Half Blocks** | Respects block half properties. | Required to properly place slabs, stairs, and trapdoors. |
| **Incremental States** | Respects states that require multiple placements. | Required to properly place double-slabs, candles, sea pickles, vines, snow layers etc. |
| **Misc States** | Respects miscellaneous block states. | Required to properly place doors, beds, lanterns, and other unique blocks. |
| **Exit Signs** | Automatically exits sign-edit screens. | This cancels the sign-edit screen when placing signs. Note: This breaks the auto-sign-text feature from litematica. |
| **Auto Switch** | Automatically switches to placeable blocks in the hotbar. | Disable if you need precision with which blocks you want to place. |
| **Swap Back** | Returns to the previous hotbar slot after placing. | Some servers dislike it when the player swaps between slots multiple times in a tick. Disable if necessary. |
| **Allow Inventory** | Allows blocks to be moved from inside the inventory into the hotbar. | — |
| **Stationary Move** | Only allows inventory moves while standing still. | Only enable this if your server doesn't allow inventory movements while your player is moving. |
| **Hotbar Priority** | Gives hotbar blocks priority over blocks in the inventory when placing. | Enabling this may result in less inventory movements. |
| **Slot Lock Ticks** | Number of ticks to lock the swap slot after moving an item into your hotbar. | Only necessary to configure if Anti-Override-Ticks doesn't stop misplacements on your server. |
| **Anti Override Ticks** | Number of ticks to stop server sync packets from overriding your slots. | This is for stopping misplacements. Inventory related S2C packets mess with the printer, so cancel them. |

# Frequently Asked Questions

| Question | Answer |
| :--- | :--- |
| **What is Dino Printer capable of placing?** | Stairs, slabs, rotatable blocks such as observers, hoppers, and pistons, as well as double slabs, snow layers, signs, banners, vines, and more. **Essentially everything**, with a few exceptions. |
| **Can I use Dino Printer to bypass cheat detection?** | **No.** Dino Printer is designed for servers such as 2b2t and Constantiam. It does **not** bypass cheat detection systems such as Watchdog. Dino Printer simply tries to comply with servers that have strict placement requirements. **I do not recommend using Dino Printer on servers where using cheats can result in a ban.** |
| **Can I use Dino Printer to build map art?** | **Absolutely.** Due to Dino Printer's unique placement, "Mapart Mode" is **Built-in** by default. It is excellent for placing carpets on oceans, or building staircased maps. |
| **Does Dino Printer offer any automation?** | **No.** Dino Printer is a general purpose printer. At minimum, Dino Printer can be paired with [baritone](https://github.com/cabaletta/baritone)'s own schematic builder to offer some level of automation. |
