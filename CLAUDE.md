# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**git-stats** is a tool that extracts statistics from git repositories. It has two parts:

1. **Java application** — Scans a git repository, reads commit history (changes, renamings, dates), and writes the data to CSV (or Parquet) files.
2. **Python/Jupyter notebook** (`app.ipynb`) — Loads the CSV output into [atoti](https://www.atoti.io/) for interactive OLAP analysis.

## Build & Run

### Java (requires Java 25 with preview features)

```bash
# Build
mvn compile

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=TestChangeReader

# Run the application
java --enable-preview -cp target/classes com.activeviam.tooling.gitstats.Application \
  -p /path/to/git/repo -o target/files -b main -n 1000
```

CLI options: `-p` project dir, `-o` output dir, `-b` branch, `-s` start commit (optional), `-n` commit count (default 10).

### Python (requires Python 3.11+, managed with uv)

```bash
uv run jupyter lab   # Opens the notebook
```

## Architecture (Java)

The Java app uses a **concurrent pipeline** architecture with `StructuredTaskScope` (Java preview feature) and blocking queues.

**Two program modes** exist (toggled via comment block in `Application.run()`):
- `StructuredProgram` — Active mode. Reads commits, fetches details concurrently, buffers results, writes in batches.
- `PipelineProgram` — Alternative. Uses explicit pipeline stages connected by queues with a `Multiplexer` to fan out.

**Data flow:**
1. `BranchCommitReader` — Runs `git rev-list` to enumerate commit SHAs, pushes them into a queue.
2. `ReadCommitDetails` — For each commit, concurrently fetches date (`git show --format=%ct`), file changes (`git show --numstat`), and renamings (`git show --raw`) using `StructuredTaskScope`.
3. Writer pipelines — Consume commit details and write 4 output file types: commits, branches, changes, renamings.

**Key abstractions:**
- `Action<T>` — Sealed interface (`Value | Stop`) used as queue messages to signal data or end-of-stream.
- `Queue<T>` — Bounded blocking queue wrapper.
- `Buffer<E>` — Accumulator that batches items before flushing to writers.
- `WriterPipeline<T>` — Abstract base for pipeline stages that consume a queue and write files.
- `Shell` — Executes git commands as subprocesses.

**Packages:**
- `explorer` — Git command execution and output parsing (`Shell`, `BranchCommitReader`, `ReadCommitDetails`).
- `shell` — Parsers for specific git output formats (`ChangeReader`, `CommitDateReader`, `RenameReader`).
- `orchestration` — Pipeline stages, queues, multiplexer, and write dispatching.
- `writing` — File writers (Parquet via Avro, or CSV). Base class `Writer<T>` handles Parquet; CSV writers are separate.

## Key Details

- Java 25 with `--enable-preview` is required (uses `StructuredTaskScope`, sealed interfaces with pattern matching, record patterns).
- Lombok is used extensively (`@val`, `@RequiredArgsConstructor`, `@Log`, `@Getter(lazy=true)`).
- OpenTelemetry annotations (`@WithSpan`) instrument key operations for tracing.
- The project is a JPMS module (`module-info.java` at root, not in `src`).
- Output formats: CSV files (currently active) or Parquet files. File naming uses `String.format` patterns like `changes-%04d.csv`.
- Tests use JUnit 5 with AssertJ assertions.
