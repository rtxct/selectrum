# Selectrum

**Selectrum** is a JetBrains plugin that automatically switches the IDE's UI theme and editor color scheme based on the time of day. It reads a local YAML configuration file to schedule theme transitions and updates the IDE appearance in the background.

## Installation

1. Open your JetBrains IDE.
2. Navigate to **Settings/Preferences** > **Plugins** > **Marketplace**.
3. Search for **Selectrum**.
4. Click **Install** and restart the IDE if prompted.

## Configuration

To set up your daily schedule, go to **Tools → Selectrum → Open Configuration** from the main menu at the very top of the IDE window. This will open the `selectrum.yaml` configuration file.

### Configuration Format

The configuration uses a simple YAML format. Each entry under `schedule` is an hour in 24h format (`H:mm` or `HH:mm`). The last entry whose hour has passed will determine the active theme.

```yaml
# Selectrum — Theme Schedule Configuration

schedule:
  # At 8:00 AM, apply the 'IntelliJ Light' theme
  08:00:
    theme: IntelliJ Light
    
  # At 6:00 PM, apply the 'Darcula' theme and explicitly use the 'Monokai' editor scheme
  18:00:
    theme: Darcula
    editor: Monokai
```

### Fields:
- **`theme`** (required): The exact name of an installed IDE theme. *(Find available themes in Settings → Appearance & Behavior → Appearance → Theme)*
- **`editor`** (optional): The name of an editor color scheme. If omitted, Selectrum will try to use the scheme that matches the `theme` name. *(Find available editors in Settings → Editor → Color Scheme)*

## Usage

Once your schedule is configured and saved, Selectrum takes care of the rest automatically. 

If you temporarily need to disable automatic switching, click the **Selectrum** widget in the bottom-right status bar of your IDE and uncheck **Theme Switching**. Click it again to resume your schedule.