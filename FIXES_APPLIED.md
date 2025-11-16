# Loot Editor Fixes Applied - November 15, 2025

## Summary
Fixed critical issues preventing loot tables from loading in Minecraft 1.21.1.

---

## Issues Fixed

### 1. **Minecraft 1.21+ Folder Naming Convention**
**Problem:** Minecraft 1.21 changed datapack folder names from plural to singular.

**Changes Made:**
- ✅ `loot_tables` → `loot_table` (singular)
- ✅ Updated in 5 code locations:
  - `DataPackService.java:47`
  - `OverridePaths.java:11`
  - `LootTableService.java:186`
  - `ManifestLootImporter.java:122`
  - `ManifestLootImporter.java:211`

**Impact:** All loot table exports now use the correct folder structure for Minecraft 1.21.1.

---

### 2. **Invalid Animation Frame Items in Catalog**
**Problem:** Application was treating animation frame models as separate items, creating invalid IDs like `minecraft:clock_10` and `minecraft:clock_46`.

**Root Cause:** Minecraft has animation frames for certain items:
- Clock: 64 frames (`clock_00.json` through `clock_63.json`)
- Compass: 32 frames (`compass_00.json` through `compass_31.json`)
- Recovery Compass: 32 frames (`recovery_compass_00.json` through `recovery_compass_31.json`)

**Changes Made:**
- ✅ Added `isAnimationFrameModel()` method in `ItemCatalogService.java:142-157`
- ✅ Added filter in `handleModelDir()` at line 126
- ✅ Uses regex to skip animation frame files: `clock_\d{2}\.json`, `compass_\d{2}\.json`, `recovery_compass_\d{2}\.json`

**Impact:** Item catalog now only shows valid items. No more invalid animation frame IDs.

---

## Files Modified

### loot-editor-b (Application)
1. `src/main/java/dev/badgersnacks/looteditor/services/DataPackService.java`
   - Line 47: Changed `loot_tables` to `loot_table`

2. `src/main/java/dev/badgersnacks/looteditor/persistence/OverridePaths.java`
   - Line 11: Changed constant from `loot_tables` to `loot_table`

3. `src/main/java/dev/badgersnacks/looteditor/services/LootTableService.java`
   - Line 186: Changed KubeJS path to use `loot_table`

4. `src/main/java/dev/badgersnacks/looteditor/tools/ManifestLootImporter.java`
   - Line 122: Changed import path to `loot_table`
   - Line 211: Changed KubeJS resolution to `loot_table`

5. `src/main/java/dev/badgersnacks/looteditor/catalog/ItemCatalogService.java`
   - Line 126: Added filter `!isAnimationFrameModel(path)`
   - Lines 142-157: Added new method `isAnimationFrameModel()`

### loot-editor-loader (Mod)
- No changes required (already working correctly)

---

## Backups Created

### Original Backups (Pre-Fix)
- `loot-editor-b.backup_20251115_202627` (~289 MB)
- `loot-editor-loader.backup_20251115_202634` (~365 MB)

### Working Backups (Post-Fix)
- `loot-editor-b.backup_20251115_205135_WORKING` (~289 MB)
- `loot-editor-loader.backup_20251115_205135_WORKING` (~369 MB)

**Total Backup Size:** ~1.3 GB

---

## Testing Results

### Before Fix
- ❌ Vanilla loot table edits: Not loading
- ❌ Custom loot tables: Not loading
- ❌ Error in logs: "Couldn't parse element... Unknown registry key: minecraft:clock_10"

### After Fix
- ✅ Vanilla loot table edits: Working
- ✅ Custom loot tables: Working
- ✅ No parsing errors in logs
- ✅ `/loot spawn` commands function correctly

---

## How to Use

### To Restore Original (Pre-Fix) Version:
```bash
cd "C:\Users\BadgerSnacks\curseforge\minecraft\Instances\Mod Testing"
rm -rf loot-editor-b
cp -r loot-editor-b.backup_20251115_202627 loot-editor-b
```

### To Restore Working (Post-Fix) Version:
```bash
cd "C:\Users\BadgerSnacks\curseforge\minecraft\Instances\Mod Testing"
rm -rf loot-editor-b
cp -r loot-editor-b.backup_20251115_205135_WORKING loot-editor-b
```

---

## Next Steps

1. **Re-scan Item Catalog** - Open the application and refresh the item catalog to remove animation frame items
2. **Re-edit Existing Loot Tables** - Any tables created with animation frame items should be updated
3. **Test Thoroughly** - Verify all loot tables work correctly in-game with `/reload` and `/loot spawn`

---

## Technical Notes

### Minecraft 1.21 Breaking Changes
In snapshot 24w21a, Mojang standardized datapack folder naming:
- `loot_tables` → `loot_table`
- `functions` → `function`
- `structures` → `structure`
- `advancements` → `advancement`
- `predicates` → `predicate`
- `item_modifiers` → `item_modifier`

### Animation Frame Files
Minecraft uses multiple model files for animated items. These are NOT valid item IDs:
- Clock has 64 time-of-day animation frames
- Compass has 32 directional animation frames
- Recovery Compass has 32 directional animation frames

Only the base model file (`clock.json`, `compass.json`, etc.) represents the actual item.

---

## Build Information
- **Build Date:** November 15, 2025
- **Minecraft Version:** 1.21.1
- **NeoForge Version:** 21.1.215
- **Pack Format:** 48
- **Application Version:** 0.1.0
- **Mod Version:** 0.1.0

---

## Issues Resolved
- ✅ Loot tables not loading in Minecraft 1.21.1
- ✅ Invalid item IDs appearing in item catalog
- ✅ Custom namespace loot tables failing to parse
- ✅ Animation frame models treated as separate items

---

*Generated by Claude Code - Session Date: November 15, 2025*
