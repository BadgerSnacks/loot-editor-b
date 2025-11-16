# Note from developer

This project was created entirely using Chat-GPT's Codex and Claude Code. I know there is a lot of contriversy surrounding AI right now but the purpose of the this project is a proof of concept that even with no coding experiance we can create wonderful things. If you have issues with the use of AI I suggest you not participate in this project. Even if this project stands a baseboard for something better then it was well worth the time.

# Loot Editor B

Loot Editor B is a desktop utility (Java 17 + JavaFX) that understands an entire Minecraft modpack: it scans vanilla loot, datapacks, and every mod jar, then makes the results editable through a point-and-click UI. You do **not** need modding experience—if you can point the app at your CurseForge instance, you can inspect and tweak loot tables.

Works with **Minecraft 1.21.1** and **NeoForge 21.1.x** via the companion **loot-editor-loader** mod.

---

## Download Standalone Version

**Don't want to build from source?** Download the latest standalone release:

- **[Download image.zip](../../releases)** (~42 MB, includes Java runtime)
- Extract anywhere and run:
  - **Windows:** Double-click `LootEditorB.vbs` (no console window)
  - **Windows (alternative):** Run `bin/loot-editor-b.bat`
  - **Mac/Linux:** Run `bin/loot-editor-b`

No Java installation required! The standalone version includes everything you need.

---

## Prerequisites

### For the Standalone Version
- **loot-editor-loader mod** (NeoForge mod for Minecraft 1.21.1) - **Recommended**
  - Download from [loot-editor-loader releases](https://github.com/BadgerSnacks/loot-editor-loader)
  - Place in your modpack's `mods/` folder
- **OR KubeJS mod** (experimental, untested alternative)
  - Only if you prefer KubeJS over the dedicated loader mod

### For Building from Source
- [JDK 17+](https://adoptium.net/)
- [Gradle 8.5+](https://gradle.org/) (or use the included wrapper)
- **loot-editor-loader mod** or **KubeJS mod** (see above)

---

## Quick Start (for newcomers)

### 1. Install a loader mod

**Option A: loot-editor-loader (Recommended)**

The **loot-editor-loader** mod is a NeoForge mod that automatically loads loot table datapacks from the application. Without a loader mod, your edits won't appear in-game.

1. Download **loot-editor-loader** for Minecraft 1.21.1 / NeoForge 21.1.x
2. Place the `.jar` file in your modpack's `mods/` folder
3. The mod will automatically register the `loot_editor` datapack location

**Option B: KubeJS (Experimental, Untested)**

Alternatively, you can use KubeJS to load loot tables (not tested with Minecraft 1.21.1):
1. Install KubeJS mod in your modpack
2. Configure to load from `kubejs/data/` folder
3. Use "Fork to KubeJS" in the application instead of "Fork to Datapack"

### 2. Launch the app

**Standalone version:**
- Extract `image.zip` and run `LootEditorB.vbs` (Windows) or `bin/loot-editor-b` (Mac/Linux)

**Building from source:**
```powershell
cd loot-editor-b
.\gradlew.bat run
```

### 3. Open your modpack
Click **Open Modpack** > pick the CurseForge instance folder (the one that contains `mods/`, `saves/`, etc.). The app records the five most recent packs so you can reopen them from the split button next time.

### 4. In-game prep (one-time per pack)
Open a disposable world in Minecraft and run:
```
/ct dump loottables
/ct dump enchantments
```
CraftTweaker writes the dump files that Loot Editor uses for manifest comparisons and the enchantment palette.

### 5. Let the scan finish
The status bar will read `Loot tables: ### | Items: ###` once both the loot tree and icon catalog are ready. Icons may appear grey for a second while textures load; they refresh automatically when the catalog completes.

That's it—you can now browse loot tables, edit them, attach enchantment pools, and save changes into:
- `datapacks/loot_editor/` (when using loot-editor-loader mod)
- `kubejs/data/` (when using KubeJS export)

---

## Export Methods

Loot Editor B supports two methods for exporting loot tables to Minecraft:

### Method 1: loot-editor-loader Mod (Recommended)

**Status:** ✅ Tested and working with Minecraft 1.21.1

The **loot-editor-loader** mod uses NeoForge's datapack system to automatically load loot tables from:
- `<modpack>/datapacks/loot_editor/` (main export location)
- `<world>/datapacks/loot_editor/` (world-specific overrides)
- Custom paths defined in `loot-editor-loader-config.toml`

**Key features:**
- Automatically registers the `loot_editor` datapack on game startup
- Supports live reloading with `/reload` command
- Works with vanilla and modded loot tables
- Compatible with Minecraft 1.21.1's new singular folder naming (`loot_table/` instead of `loot_tables/`)

**Config location:** `<modpack>/config/loot-editor-loader-config.toml`

### Method 2: KubeJS (Experimental)

**Status:** ⚠️ Not tested with Minecraft 1.21.1

The application can export loot tables to KubeJS's data folder structure:
- `<modpack>/kubejs/data/<namespace>/loot_table/<path>.json`

**Notes:**
- This method has not been tested with the current version
- KubeJS may have different compatibility requirements
- Use at your own risk - the loot-editor-loader method is recommended

To use KubeJS export, you would need:
- KubeJS mod installed in your modpack
- KubeJS configured to load data from `kubejs/data/` folder

---

## Everyday Workflow

### 1. Browse and open loot tables
- Use the left-hand tree to filter by **All / Chests / Blocks / Entities**.
- Double-click any leaf node (e.g., `minecraft/chests/ancient_city`) to load it into the editor pane.
- The **Inspector** tab shows the path on disk plus whether the table is editable. If it lives inside a mod jar, use **Fork to Datapack** or **Fork to KubeJS** to copy it to an editable location before editing.

### 2. Edit drops in the Loot Entries list
- There is a single list labeled **Loot Entries**. Each row displays the item icon, friendly name, weight, and min/max count.
- **Add entries**
  - **Item Palette** tab: search by name or mod, then double-click (or drag) to insert that item.
  - The palette uses the same icons as JEI; if you add a new resource pack or mod, click **Rescan** so Loot Editor rebuilds the catalog.
- **Adjust weights and counts**
  - Use the spinners in each row. Changes are live-updated in the JSON preview at the bottom.
  - The row gains a "New" or "Edited" badge until you save.

### 3. Work with Enchantment Pools
- Switch to the **Enchant Pools** tab to create reusable pools (e.g., "Rare Sword Enchants").
- **Hide noise:** The "Show empty pools" toggle filters out pools that currently have zero enchantments so the list stays concise.
- **Add enchantments via palette:** The panel on the right lists every enchantment discovered in `ct_dumps/enchantment.txt`. **Double-click** an entry to add it to the pool table. You can filter by mod namespace (e.g., `minecraft`, `apotheosis`) or search by name ("Swift Sneak").
- **Attach a pool to an item:** Select a loot row, highlight the pool you want, then click **Attach To Selection**. The row now shows a badge such as `Rare Sword Enchants (loot_editor:rare_swords)` and, when saved, the app emits multiple loot entries with the correct `minecraft:set_enchantments` functions.
- **Remove a pool:** Use the **Clear Pool** button inside the row or the "Clear Pool" button in the pool tab.
- Switching between pools now warns you if the current form has unsaved edits, so save or discard before hopping to another pool. When a pool is dirty, a yellow banner appears with **Save Now** and **Discard Changes** buttons for quick control.

### 4. Save & test
- Click **Save Loot Table**. The app writes JSON to:
  - **Datapack export:** `datapacks/loot_editor/data/<namespace>/loot_table/<path>.json` (recommended)
  - **KubeJS export:** `kubejs/data/<namespace>/loot_table/<path>.json` (experimental, untested)
- When using datapack export, Loot Editor also mirrors that datapack into every world under `saves/<world>/datapacks/` so it is immediately enabled in-game (a datapack only loads when it sits in the world's datapacks folder).
- Close the window only after saving. Loot Editor now warns if you try to exit while loot tables or enchantment pools still have unsaved edits.
- Load your dev world and run `/reload` to pull in the changes.
- Use `/loot spawn <x> <y> <z> loot <table_id>` to test exactly what was edited. Example:
  ```
  /loot spawn ~ ~1 ~ loot minecraft:chests/ancient_city
  ```
- If something looks wrong, hit **Revert Changes** back in the app and start over.

### 5. Logs and backups
- Every major action writes to `%USERPROFILE%\.loot-editor-b\logs\loot-editor-<timestamp>.log`.
- The app never touches your existing backups. Create backups manually if needed before major edits.

---

## Building & Running (advanced)

### Development Commands
1. `./gradlew run` – launch the JavaFX UI.
2. `./gradlew build` – produce a runnable JAR under `build/libs/`.
3. `./gradlew test` – run unit tests (covers JSON rebuilds, scanner logic, etc.).
4. Supply a pack path directly:
   ```
   ./gradlew run -Dlauncher="C:/Users/<you>/curseforge/minecraft/Instances/YourPack"
   ```

### Creating Standalone Distribution

Need to hand the tool to someone who does not have Java installed? Use the bundled Badass Runtime Plugin:

1. **`./gradlew runtime`** – Builds a trimmed Java 17 runtime plus the application under `build/image/`. The generated `bin/loot-editor-b(.bat)` scripts inject `--module-path "$APP_HOME/lib" --add-modules javafx.controls,javafx.fxml`, so the JavaFX bits bundled in `lib/` are always on the module path.

2. **`./gradlew runtimeZip`** – Zips that folder into `build/image.zip` for fast sharing (~42 MB). Drop it on a flash drive or share via GitHub releases, unzip anywhere, launch `bin/loot-editor-b`.

3. **Windows quality-of-life:** Double-click `LootEditorB.vbs` (generated next to `bin/`) to launch the batch script hidden. If you do not mind a console window, `bin/loot-editor-b.bat` still works.

4. **Want a native installer?** Install the platform tooling first (e.g., [WiX Toolset](https://wixtoolset.org/) on Windows) and then run `./gradlew jpackage`. Without WiX you can stick to the runtime zip above.

---

## Headless Manifest / CLI Tools

Need loot coverage without launching the UI? First, in a disposable test world run:
```
/ct dump loottables
```
CraftTweaker writes the list to `logs/crafttweaker.log` (older versions also generate `ct_dumps/lootTables.txt`). Once the dump exists, use the bundled Gradle wrapper (Unix: `./gradlew`, Windows: `gradlew.bat`) and these tasks:

- **CraftTweaker dump importer**
  ```
  ./gradlew importCraftTweakerDump -PpackRoot="C:/path/to/pack"
  ```
  Writes `import/loot_tables_ct.json` by default. Override with `-PctManifest=...` (or `-Poutput=...` for backwards compatibility). Extra knobs:
  - `-Pdump=C:/override/lootTables.txt`
  - `-PlogFile=C:/pack/logs/crafttweaker.log` (if the log lives somewhere else)

- **Static scanner (mods/datapacks)**
  ```
  ./gradlew scanLootTables -PpackRoot="C:/path/to/pack"
  ```
  Produces `import/loot_tables_scan.json` (override with `-PscanManifest=...` or `-Poutput=...`).

- **Merge both manifests**
  ```
  ./gradlew mergeLootManifests [-PctManifest=...] [-PscanManifest=...] [-PmergedManifest=...]
  ```
  Consumes the two JSON files above and emits `import/loot_tables_merged.json`.

- **One-shot refresh**
  ```
  ./gradlew refreshLootIndex -PpackRoot="C:/path/to/pack"
  ```
  Runs importer + scanner + merger in sequence, keeping the defaults (`import/loot_tables_*.json`) up to date.

- **Import directly from mod/vanilla jars**
  ```
  ./gradlew importJarLoot -PpackRoot="C:/path/to/pack" -Ppatterns="chests/,entities/"
  ```
  Scans every `mods/*.jar` (plus the vanilla jar) for loot tables listed in the merged manifest and copies the ones matching the patterns into `datapacks/loot_editor/data/` (or `kubejs/data/` if using KubeJS export). Combine with `-Pnamespaces=minecraft,astral_dimension` or `-PdryRun=true` for finer control.

### UI integration
When loot-editor-b launches, it now looks for `import/loot_tables_merged.json` (or any path supplied via the JVM flag `-Dloot.manifest="C:/custom/path.json"`). If the manifest targets the same modpack that is currently open, a toolbar badge shows whether every manifest entry exists in the live scan; missing entries are listed in the tooltip, making it easy to spot CraftTweaker-only tables that still need to be copied into datapacks.

---

## Tips & Troubleshooting

- **Icons missing?** Use **Rescan** after adding mods/resource packs; the app clears its icon cache once the catalog finishes so textures refresh automatically.

- **Enchantment palette empty?** Ensure `/ct dump enchantments` was run in-game and re-open the modpack. The palette reads `ct_dumps/enchantment.txt`.

- **Tables not taking effect in-world?**
  - **Using datapack export (recommended):**
    - Confirm the **loot-editor-loader mod** is installed in your `mods/` folder.
    - Confirm you edited the copy in `datapacks/loot_editor/` (look for "Editable: true" in Inspector).
    - Check that the world's `datapacks/` folder contains the `loot_editor` datapack.
  - **Using KubeJS export (experimental):**
    - Confirm KubeJS mod is installed and configured correctly.
    - Check that tables are in `kubejs/data/<namespace>/loot_table/<path>.json`.
    - Note: This method has not been tested with Minecraft 1.21.1.
  - Some datapacks override vanilla loot. Check `minecraftinstance.json` and the world's `datapacks/` folder to see what takes priority.
  - Make sure you're testing in a **new** dungeon chest; naturally generated structures keep the loot table that existed when the chunk was created.
  - Run `/reload` after saving changes in the app.

- **Minecraft 1.21.1 folder naming:** This version uses singular folder names (`loot_table/` not `loot_tables/`). The application has been updated to use the correct naming convention.

---

## Companion Mod

**loot-editor-loader** is a NeoForge mod that loads the datapack automatically. Without it, loot tables won't take effect in-game.

- **Repository:** [BadgerSnacks/loot-editor-loader](https://github.com/BadgerSnacks/loot-editor-loader)
- **Minecraft Version:** 1.21.1
- **NeoForge Version:** 21.1.x
- **Java Version:** 21

---

Happy editing!
