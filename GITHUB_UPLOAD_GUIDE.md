# GitHub Upload Guide for Loot Editor B

## What to Upload to GitHub

### ✅ INCLUDE These Files/Folders

#### Essential Source Code
```
src/
├── main/
│   └── java/
│       └── dev/badgersnacks/looteditor/
│           ├── agents/
│           ├── catalog/
│           ├── logging/
│           ├── manifest/
│           ├── model/
│           ├── persistence/
│           ├── scanner/
│           ├── services/
│           ├── tools/
│           ├── ui/
│           ├── util/
│           └── LootEditorApp.java
└── test/
    └── java/
```

#### Build Configuration
```
build.gradle          ← Gradle build script
settings.gradle       ← Gradle settings
gradle/               ← Gradle wrapper files
  └── wrapper/
      ├── gradle-wrapper.jar
      └── gradle-wrapper.properties
gradlew               ← Gradle wrapper script (Unix)
gradlew.bat           ← Gradle wrapper script (Windows)
```

#### Documentation & Assets
```
README.md             ← Project documentation
loot-table-plan.txt   ← Design documentation
debug.png             ← Project asset/screenshot
.gitignore            ← Git ignore rules (NEWLY CREATED)
```

---

### ❌ DO NOT INCLUDE These Files/Folders

#### Build Artifacts (265 MB!)
```
build/                ← Compiled classes, JARs, distributions
.gradle/              ← Gradle cache (1.1 MB)
```

#### Runtime/User Data (23 MB!)
```
import/               ← Scanned loot table data (user-specific)
export/               ← Exported datapacks (user-specific)
importlog.txt         ← Runtime log file
```

#### User-Specific Settings
```
export-settings.json  ← User's local path configuration
```

#### IDE Files (if they exist)
```
.idea/                ← IntelliJ IDEA
*.iml                 ← IntelliJ IDEA module files
.vscode/              ← Visual Studio Code
.settings/            ← Eclipse
.project              ← Eclipse
.classpath            ← Eclipse
```

---

## How to Upload to GitHub

### Option 1: Using Git Command Line

1. **Initialize Git repository** (if not already done):
   ```bash
   cd "C:\Users\BadgerSnacks\curseforge\minecraft\Instances\Mod Testing\loot-editor-b"
   git init
   ```

2. **Add the .gitignore file** (already created):
   ```bash
   git add .gitignore
   git commit -m "Add .gitignore file"
   ```

3. **Add all source files**:
   ```bash
   git add .
   git commit -m "Initial commit - Loot Editor B application"
   ```

4. **Create repository on GitHub**:
   - Go to https://github.com/new
   - Name it: `loot-editor-b` or `minecraft-loot-editor`
   - Don't initialize with README (you already have one)
   - Click "Create repository"

5. **Push to GitHub**:
   ```bash
   git remote add origin https://github.com/YOUR-USERNAME/loot-editor-b.git
   git branch -M main
   git push -u origin main
   ```

### Option 2: Using GitHub Desktop

1. **Open GitHub Desktop**
2. **File → Add Local Repository**
3. **Choose** `C:\Users\BadgerSnacks\curseforge\minecraft\Instances\Mod Testing\loot-editor-b`
4. **Initialize Git repository** if prompted
5. **Commit all files** with message: "Initial commit - Loot Editor B"
6. **Publish repository** to GitHub

### Option 3: Using GitHub Web Upload

1. **Create a ZIP of ONLY the required files**:
   ```bash
   # This would need to be done manually, selecting only the files listed in the INCLUDE section
   ```

2. **Go to https://github.com/new**
3. **Create repository**
4. **Upload files** via the web interface

---

## Directory Size Summary

**Before Upload (Total: ~289 MB):**
- build/ → 265 MB ❌ (excluded)
- import/ → 23 MB ❌ (excluded)
- .gradle/ → 1.1 MB ❌ (excluded)
- Source + docs → ~1 MB ✅ (included)

**After Upload (Total: ~1 MB)**
- Only source code, build scripts, and documentation

---

## Verify Before Pushing

Run this command to see what will be committed:
```bash
cd "C:\Users\BadgerSnacks\curseforge\minecraft\Instances\Mod Testing\loot-editor-b"
git status
```

Run this to see what is being ignored:
```bash
git status --ignored
```

---

## Clone Instructions for Others

Once uploaded, others can clone and build with:

```bash
# Clone the repository
git clone https://github.com/YOUR-USERNAME/loot-editor-b.git
cd loot-editor-b

# Build and run (Gradle will download dependencies automatically)
./gradlew run

# Or build a distribution
./gradlew build
```

---

## Important Notes

1. **The .gitignore file prevents accidentally committing:**
   - Build artifacts (saves ~265 MB)
   - User data (saves ~23 MB)
   - IDE files
   - Runtime logs

2. **Gradle wrapper is included** so others don't need to install Gradle

3. **No secrets or credentials** are in the codebase (good!)

4. **Dependencies are managed by Gradle** and will be downloaded automatically

---

## Recommended Repository Settings

### Repository Name
- `minecraft-loot-editor` or `loot-editor-b`

### Description
```
A JavaFX application for visually editing Minecraft 1.21.1 loot tables.
Works with NeoForge mods and datapacks.
```

### Topics (tags)
```
minecraft
loot-tables
javafx
neoforge
minecraft-modding
datapack-editor
gui-application
```

### README Badges (optional)
```markdown
![Java](https://img.shields.io/badge/Java-17-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-20-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)
![License](https://img.shields.io/badge/license-MIT-blue)
```

---

## File Structure on GitHub

```
loot-editor-b/
├── .gitignore                    ← Ignore rules
├── README.md                     ← Project overview
├── build.gradle                  ← Build configuration
├── settings.gradle               ← Gradle settings
├── gradlew                       ← Build script (Unix)
├── gradlew.bat                   ← Build script (Windows)
├── debug.png                     ← Screenshot
├── loot-table-plan.txt           ← Design docs
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    ├── main/
    │   └── java/
    │       └── dev/badgersnacks/looteditor/
    │           └── [all source files]
    └── test/
        └── java/
```

**Total Size:** ~1 MB (vs 289 MB with build artifacts)

---

*Created: November 15, 2025*
*This guide ensures only necessary project files are uploaded to GitHub.*
