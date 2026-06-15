# IC2 recipe conversion notes

This module imports the old IndustrialCraft 2 experimental recipe set into Minecraft/Forge 26.1.2 data JSON format.

## What was converted

- Shaped crafting recipes generated from `shaped_recipes.ini`
- Shapeless crafting recipes generated from `shapeless_recipes.ini`
- Furnace/smelting recipes generated from `furnace.ini`
- Simple placeholder items/blocks needed as recipe ingredients/results
- Item/block models, item definitions, blockstates, simple block models and loot tables for generated entries

## Project substitutions

- IC2 copper ingredients use vanilla `minecraft:copper_ingot` and `minecraft:copper_block` where possible.
- Sticky resin was replaced by vanilla `minecraft:resin_brick`.
- `minecraft:resin_brick` smelts into `ic2reborn:rubber`.
- IC2 scaffold recipes were skipped because vanilla Minecraft already has scaffolding.

## Crafting tools

The generated `hammer` and `cutter` items have durability. Recipes consume them like normal ingredients, then `CraftingToolEvents` returns the same tool damaged by 1 durability point:

- Hammer returns for plates, dense plates and casings.
- Cutter returns for cables.

## Intentional skips

Some original IC2 config entries were intentionally not converted as plain crafting JSON because they depend on IC2 machine/fluid behavior or would overwrite vanilla recipes. Those should be implemented later when the corresponding machine systems exist.
