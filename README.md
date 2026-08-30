<div align="center">
  <img src="/src/main/resources/assets/dinoprinter/icon.png" alt="logo" width="20%"/>
  <h1>
    Dino Printer<br>
    <sub>by dinosaurwizard</sub>
  </h1>

  <p>A robust printer for Litematica schematics.</p>
</div>

<hr />

## Overview

**Dino Printer** is an all-purpose 'just works' schematic printer designed for servers with strict anti-cheat systems (or not!).

It is built from scratch and is **not a fork of any existing Meteor printer**. Because of this, Dino Printer has its own placement system rather than relying on the same methods used by other printers.

The overarching goal of Dino Printer is to provide reliable schematic printing while still supporting advanced features that are difficult to implement on stricter servers.

## How It Works

Dino Printer uses a dynamic system to determine **how each block should be placed**.

When the printer needs to place a block, it first looks at the blocks surrounding the target position. It examines the shape of each adjacent block and generates possible points on their surfaces where the new block could be placed.

For every possible placement point, Dino Printer performs a series of checks, such as:

* Can the point be seen by the player? (If using raytracing)
* What direction will the block face if placed here?
* If stairs or slabs, will the block be placed in the correct half?
* Will the resulting block state match the schematic?

Instead of simply assuming how a block should be placed, Dino Printer **simulates the placement at each possible point** and examines the resulting `BlockState`.

This allows it to determine the correct placement position and rotation without needing to configure special cases for individual blocks.

This makes the system highly flexible and allows it to work with **nearly all Minecraft blocks**.

## Features

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
