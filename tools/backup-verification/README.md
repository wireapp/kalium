# Wire Backup Verification Tool

A desktop GUI application built with Jetpack Compose Desktop that compares two or more Wire backup files (`.wbu`) to identify differences in their contents.

## What It Does

The tool imports Wire backup files and compares their messages, users, and conversations across backups. Results are categorized into three tabs:

- **Equal Items** - Items that are identical across all selected backups.
- **Missing Items** - Items present in some backups but absent from others.
- **Different Items** - Items that exist in multiple backups but have differing content.

This is useful for verifying backup integrity, ensuring backup/restore consistency, or debugging issues in the backup pipeline.

## How to Run

From the Kalium root directory:

```bash
./gradlew :tools:backup-verification:run
```

This launches the desktop application window.

## Usage

1. Click **Add Files** to select two or more `.wbu` backup files.
2. The tool peeks into each file and displays metadata (version, encryption status).
3. If a backup is encrypted, enter its password in the text field shown for that file.
4. Once at least two files are selected, click **Run Backup Comparison**.
5. Results are displayed in three tabs: Equal Items, Missing Items, and Different Items.

If a file fails to import (e.g., wrong password), the tool returns to the file selection screen and highlights the failed file.

## Requirements

- JDK 21
- A desktop environment (the tool uses Compose Desktop for its GUI)
