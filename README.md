# Loot Editor B

Loot Editor B is a desktop utility (Java 17 + JavaFX) that understands an entire Minecraft modpack: it scans vanilla loot, datapacks, KubeJS exports, and every mod jar, then makes the results editable through a point‑and‑click UI. You do **not** need modding experience—if you can point the app at your CurseForge instance, you can inspect and tweak loot tables.

---

## Quick Start (for newcomers)

1. **Install prerequisites**
   - [JDK 17+](https://adoptium.net/)
   - [Gradle 8.5+](https://gradle.org/) (or use the included wrapper)
2. **Launch the app**
   ```powershell
   cd C:\Users\<you>\curseforge\minecraft\Instances\DBB2 Dev\loot-editor-b
   .\gradlew.bat run
   ```
3. **Open your modpack.** Click **Open Modpack** > pick the CurseForge instance folder (the one that contains `mods/`, `kubejs/`, etc.). The app records the five most recent packs so you can reopen them from the split button next time.
4. **In-game prep (one-time per pack).** Open a disposable world in Minecraft and run:
   ```
   /ct dump loottables
   /ct dump enchantments
   ```
   CraftTweaker writes the dump files that Loot Editor uses for manifest comparisons and the enchantment palette.
5. **Let the scan finish.** The status bar will read `Loot tables: ### | Items: ###` once both the loot tree and icon catalog are ready. Icons may appear grey for a second while textures load; they refresh automatically when the catalog completes.

That’s it—you can now browse loot tables, edit them, attach enchantment pools, and save changes back into `kubejs/data`.

---

## Everyday Workflow

### 1. Browse and open loot tables
- Use the left-hand tree to filter by **All / Chests / Blocks / Entities**.
- Double-click any leaf node (e.g., `kubejs/minecraft/chests/ancient_city`) to load it into the editor pane.
- The **Inspector** tab shows the path on disk plus whether the table is editable. If it lives inside a mod jar, hit **Fork to KubeJS** to copy it into `kubejs/data` before editing.

### 2. Edit drops in the Loot Entries list
- There is a single list labeled **Loot Entries**. Each row displays the item icon, friendly name, weight, and min/max count.
- **Add entries**
  - **Item Palette** tab: search by name or mod, then double-click (or drag) to insert that item.
  - The palette uses the same icons as JEI; if you add a new resource pack or mod, click **Rescan** so Loot Editor rebuilds the catalog.
- **Adjust weights and counts**
  - Use the spinners in each row. Changes are live-updated in the JSON preview at the bottom.
  - The row gains a “New” or “Edited” badge until you save.

### 3. Work with Enchantment Pools
- Switch to the **Enchant Pools** tab to create reusable pools (e.g., “Rare Sword Enchants”).
- **Hide noise:** The “Show empty pools” toggle filters out pools that currently have zero enchantments so the list stays concise.
- **Add enchantments via palette:** The panel on the right lists every enchantment discovered in `ct_dumps/enchantment.txt`. **Double-click** an entry to add it to the pool table. You can filter by mod namespace (e.g., `minecraft`, `apotheosis`) or search by name (“Swift Sneak”).
- **Attach a pool to an item:** Select a loot row, highlight the pool you want, then click **Attach To Selection**. The row now shows a badge such as `Rare Sword Enchants (loot_editor:rare_swords)` and, when saved, the app emits multiple loot entries with the correct `minecraft:set_enchantments` functions.
- **Remove a pool:** Use the **Clear Pool** button inside the row or the “Clear Pool” button in the pool tab.
- Switching between pools now warns you if the current form has unsaved edits, so save or discard before hopping to another pool. When a pool is dirty, a yellow banner appears with **Save Now** and **Discard Changes** buttons for quick control.

### 4. Save & test
- Click **Save Loot Table**. The app writes JSON, stores metadata about pooled entries, and tells you if the file is read-only.
- Saved overrides now land in `datapacks/loot_editor/...`. Each time you save/export, Loot Editor also mirrors that datapack into every world under `saves/<world>/datapacks/` so it is immediately enabled in-game (a datapack only loads when it sits in the world’s datapacks folder). The **Export Datapack** button writes the active modpack automatically; **Fork to KubeJS** stays available for legacy workflows.
- Close the window only after saving. Loot Editor now warns if you try to exit while loot tables or enchantment pools still have unsaved edits.
- Load your dev world and run `/reload` (or `/kubejs reload server_scripts`) to pull in the changes.
- Use `/loot spawn <x> <y> <z> loot <table_id>` to test exactly what was edited. Example:
  ```
  /loot spawn 0 100 0 loot kubejs:minecraft/chests/ancient_city
  ```
- If something looks wrong, hit **Revert Changes** back in the app and start over.

### 5. Logs and backups
- Every major action writes to `%USERPROFILE%\.loot-editor-b\logs\loot-editor-<timestamp>.log`.
- The app never touches your existing backups. Use the repo’s `backups/loot-editor-b-<timestamp>` folders (created via `robocopy`) to roll back the entire project if needed.

---

## Building & Running (advanced)
1. `./gradlew run` – launch the JavaFX UI.
2. `./gradlew build` – produce a runnable JAR under `build/libs/`.
3. `./gradlew test` – run unit tests (covers JSON rebuilds, scanner logic, etc.).
4. Supply a pack path directly:
   ```
   ./gradlew run -Dlauncher="C:/Users/<you>/curseforge/minecraft/Instances/DBB2 Dev"
   ```

### Redistributable runtime image (Option 3)
Need to hand the tool to someone who does not have JavaFX set up? Use the bundled Badass Runtime Plugin flow:

1. `./gradlew runtime` builds a trimmed Java 17 runtime plus the application under `build/image/`. The generated `bin/loot-editor-b(.bat)` scripts now inject `--module-path "$APP_HOME/lib" --add-modules javafx.controls,javafx.fxml`, so the JavaFX bits bundled in `lib/` are always on the module path.
2. `./gradlew runtimeZip` zips that folder into `build/image.zip` for fast sharing (drop it on a flash drive or Discord, unzip anywhere, launch `bin/loot-editor-b`).
3. Windows quality-of-life: double-click `LootEditorB.vbs` (generated next to `bin/`) to launch the batch script hidden. If you do not mind a console window, `bin/loot-editor-b.bat` still works.
4. Want a native installer later? Install the platform tooling first (e.g., [WiX Toolset](https://wixtoolset.org/) on Windows) and then run `./gradlew jpackage`. Without WiX you can stick to the runtime zip above.

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

- **Static scanner (mods/datapacks/KubeJS)**
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
  Scans every `mods/*.jar` (plus the vanilla jar) for loot tables listed in the merged manifest and copies the ones matching the patterns into `kubejs/data`. Combine with `-Pnamespaces=minecraft,astral_dimension` or `-PdryRun=true` for finer control.

### UI integration
When loot-editor-b launches, it now looks for `import/loot_tables_merged.json` (or any path supplied via the JVM flag `-Dloot.manifest="C:/custom/path.json"`). If the manifest targets the same modpack that is currently open, a toolbar badge shows whether every manifest entry exists in the live scan; missing entries are listed in the tooltip, making it easy to spot CraftTweaker-only tables that still need to be copied into KubeJS/datapacks.

---

## Tips & Troubleshooting
- **Icons missing?** Use **Rescan** after adding mods/resource packs; the app clears its icon cache once the catalog finishes so textures refresh automatically.
- **Enchantment palette empty?** Ensure `/ct dump enchantments` was run in-game and re-open the modpack. The palette reads `ct_dumps/enchantment.txt`.
- **Tables not taking effect in-world?**
  - Confirm you edited the copy in `kubejs/data` (look for “Editable: true” in Inspector).
  - Some datapacks override vanilla loot. Check `minecraftinstance.json` and the world’s `datapacks/` folder to see what takes priority.
  - Make sure you’re testing in a **new** dungeon chest; naturally generated structures keep the loot table that existed when the chunk was created.

Happy editing!
