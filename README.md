# git-stats

Extract statistics from git repositories and explore them interactively with [atoti](https://www.atoti.io/).

## Overview

**git-stats** has two parts:

1. **Java CLI** -- Scans a git repository and writes statistics to CSV files.
2. **Jupyter notebook** (`app.ipynb`) -- Loads the CSV output into atoti for interactive OLAP analysis.

The CLI provides two subcommands:

| Command | Output files | Description |
|---------|-------------|-------------|
| `history` | `branches-*.csv`, `commits-*.csv`, `changes-*.csv`, `renamings-*.csv` | Commit history: file additions/deletions, renamings, dates |
| `tree-stats` | `lines-*.csv`, `indentation-*.csv` | Full tree stats at each commit: line counts and indentation metrics |

## Requirements

- Java 25 (with preview features)
- Maven
- Python 3.11+ with [uv](https://docs.astral.sh/uv/) (for the notebook)

## Build

```bash
mvn package -DskipTests
```

This produces a shaded (fat) jar in `target/git-stats-0.0.1-SNAPSHOT.jar`.

## Usage

### `history` -- Extract commit history

Generates branch, commit, change, and renaming CSV files.

```bash
java --enable-preview -jar target/git-stats-0.0.1-SNAPSHOT.jar \
  history -p /path/to/repo -o output/ -b main -n 1000
```

Output:
```
output/branches-0000.csv
output/commits-0000.csv
output/changes-0000.csv
output/renamings-0000.csv
```

### `tree-stats` -- Extract tree statistics

Generates line count and indentation CSV files. Requires an indent spec (`-i`).

```bash
java --enable-preview -jar target/git-stats-0.0.1-SNAPSHOT.jar \
  tree-stats -p /path/to/repo -o output/ -b main -n 1000 -i 2s
```

Output:
```
output/lines-0000.csv
output/indentation-0000.csv
```

### CLI options

| Option | Description | Required |
|--------|-------------|----------|
| `-p`, `--project` | Path to the git repository | Yes |
| `-o`, `--output` | Output directory for CSV files | Yes |
| `-b`, `--branch` | Branch to inspect | Yes |
| `-s`, `--start` | Start commit (defaults to branch HEAD) | No |
| `-n`, `--count` | Number of commits to collect (default: 10) | No |
| `-i`, `--indent` | Indent unit: `<number><t\|s>` (e.g. `2t` for 2-tab, `4s` for 4-space). Only for `tree-stats`. | `tree-stats` only |

### Help

```bash
# General help
java --enable-preview -jar target/git-stats-0.0.1-SNAPSHOT.jar --help

# Subcommand help
java --enable-preview -jar target/git-stats-0.0.1-SNAPSHOT.jar history --help
java --enable-preview -jar target/git-stats-0.0.1-SNAPSHOT.jar tree-stats --help
```

## Explore with atoti

Once you have generated the CSV files, open the notebook to load and explore them:

```bash
uv run jupyter lab
```

Then open `app.ipynb` and point it at your output directory.

## Development

```bash
# Compile
mvn compile

# Run tests
mvn test

# Format code
mvn com.spotify.fmt:fmt-maven-plugin:format
```
