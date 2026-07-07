# Sts2CardViewer

A **Rider / ReSharper** plugin that provides a visual card and relic browser for **Slay the Spire 2** mod development. Browse custom cards and relics with live preview, filtering, and localization support — all inside your IDE.

## Features

- **Card Grid** – Browse cards in a visual grid with portraits, cost badges, type, rarity, and keyword tags
- **Relic List** – Browse relics with icons, names, and descriptions
- **Filtering** – Filter by name, card type (Attack/Skill/Power), rarity (Basic/Common/Uncommon/Rare/Ancient/Token), target, and keyword
- **Language Switching** – Automatically discovers localization folders; switch languages on the fly
- **Detail Preview** – Click any card or relic to see a large preview with full stats and flavor text
- **Editor Gutter Icon** – When opening a card `.cs` file, a gutter icon appears for quick preview
- **Auto-Detection** – Detects STS2 mod projects via `Sts2PathDiscovery.props` or Steam installation
- **Live Reload** – Automatically refreshes when `.cs` files in your mod are modified

## Screenshots

<img width="3180" height="1964" alt="image" src="https://github.com/user-attachments/assets/b9318f1e-661d-43c8-8030-84d55db06fc6" />

<img width="2820" height="1270" alt="image" src="https://github.com/user-attachments/assets/64ce25ec-efa6-4840-908f-e3a2b039c9ef" />

<img width="2928" height="1928" alt="image" src="https://github.com/user-attachments/assets/3ceeec78-246e-407c-95ab-9c2ecc09af94" />


## Requirements

- **JetBrains Rider** 2024.3+ or **ReSharper** 2024.3+ (build range `243.*` – `262.*`)
- **Slay the Spire 2** installed via Steam (for asset paths)
- **Java 17+** (bundled with Rider)

## Building from Source

**Prerequisites:** JDK 17+

```bash
git clone https://github.com/YOUR_USER/Sts2CardViewer.git
cd Sts2CardViewer
./gradlew build
```

The built plugin `.jar` will be at `build/distributions/Sts2CardViewer-*.zip`.

### Run a test IDE instance

```bash
./gradlew runIde
```

This launches a sandboxed Rider/IntelliJ with the plugin loaded.

## Installation

1. Open *Rider → Settings → Plugins → ⚙ → Install Plugin from Disk…*

## Usage

1. Open your STS2 mod project in Rider
2. Open the **STS2 Card Viewer** tool window (*View → Tool Windows → STS2 Card Viewer*)
3. The plugin auto-detects your mod and loads cards/relics
4. Use the toolbar to search, filter, and switch localization languages
5. Click any card or relic to see a detail preview on the right

## Project Structure

```
src/main/kotlin/com/jetbrains/rider/plugins/sts2cardviewer/
├── Sts2ToolWindowFactory.kt        # ToolWindow entry point
├── Sts2CardViewerSettings.kt       # Persistent settings
├── Sts2CardViewerConfigurable.kt   # Settings UI
├── model/
│   └── CardModels.kt               # Data classes
├── parser/
│   ├── CardMetadataExtractor.kt    # Scans .cs files for card classes
│   ├── RelicMetadataExtractor.kt   # Scans .cs files for relic classes
│   ├── ModDiscoverer.kt            # Detects STS2 installation & mod manifests
│   ├── LocalizationReader.kt       # Reads localization JSON
│   └── AssetPathResolver.kt        # Resolves card/relic asset paths
└── ui/
    ├── Sts2CardViewerPanel.kt      # Main panel
    ├── CardGridPanel.kt            # Card grid rendering
    ├── CardDetailPanel.kt          # Card/relic detail preview
    ├── RelicListPanel.kt           # Relic list view
    ├── FilterToolbar.kt            # Search & filter UI
    └── LanguageComboBox.kt         # Language selector
```

## License

GNU AFFERO GENERAL PUBLIC LICENSE
