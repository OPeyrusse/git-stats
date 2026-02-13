/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.explorer;

import com.activeviam.tooling.gitstats.Application.IndentSpec;
import com.activeviam.tooling.gitstats.ProgramException;
import com.activeviam.tooling.gitstats.internal.explorer.Shell.Output;
import com.activeviam.tooling.gitstats.internal.shell.ChangeReader;
import com.activeviam.tooling.gitstats.internal.shell.CommitDateReader;
import com.activeviam.tooling.gitstats.internal.shell.IndentationReader;
import com.activeviam.tooling.gitstats.internal.shell.LineCountReader;
import com.activeviam.tooling.gitstats.internal.shell.RenameReader;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.function.Predicate;
import lombok.val;

/**
 * @author ActiveViam
 */
public class ReadCommitDetails {

  public enum FetchMode {
    HISTORY,
    TREE_STATS
  }

  private final Path projectDir;
  private final String commit;
  private final IndentSpec indentSpec;
  private final FetchMode mode;
  private final boolean allFiles;

  public ReadCommitDetails(
      final Path projectDir,
      final String commit,
      final IndentSpec indentSpec,
      final FetchMode mode) {
    this(projectDir, commit, indentSpec, mode, false);
  }

  public ReadCommitDetails(
      final Path projectDir,
      final String commit,
      final IndentSpec indentSpec,
      final FetchMode mode,
      final boolean allFiles) {
    this.projectDir = projectDir;
    this.commit = commit;
    this.indentSpec = indentSpec;
    this.mode = mode;
    this.allFiles = allFiles;
  }

  private Instant readCommitDate() {
    final var output = Shell.execute(CommitDateReader.getCommand(this.commit), this.projectDir);
    final var stdout = Output.readStream(output.stdout()).trim();
    return CommitDateReader.parseLine(stdout);
  }

  private List<FileChanges> readFileChanges() {
    final var process =
        Shell.startDiscardingStderr(ChangeReader.getCommand(this.commit), this.projectDir);

    return Shell.Output.consumeStdout(
        process,
        reader ->
            reader
                .lines()
                .skip(1)
                .filter(Predicate.not(String::isBlank))
                .map(ChangeReader::parseLine)
                .toList());
  }

  private List<FileRenaming> readFileRenamings() {
    val process =
        Shell.startDiscardingStderr(RenameReader.getCommand(this.commit), this.projectDir);

    return Shell.Output.consumeStdout(
        process,
        reader ->
            reader.lines().skip(1).map(this::parseFileInfo).filter(Objects::nonNull).toList());
  }

  private FileRenaming parseFileInfo(final String line) {
    return RenameReader.parseLine(line).orElse(null);
  }

  private List<String> readChangedFiles() {
    val command = List.of("git", "diff-tree", "--no-commit-id", "--name-only", "-r", this.commit);
    val process = Shell.startDiscardingStderr(command, this.projectDir);
    return Shell.Output.consumeStdout(
        process, reader -> reader.lines().filter(Predicate.not(String::isBlank)).toList());
  }

  private List<FileLineCount> readFileLineCounts(final List<String> changedPaths) {
    val command =
        changedPaths != null
            ? LineCountReader.getCommand(this.commit, changedPaths)
            : LineCountReader.getCommand(this.commit);
    val process = Shell.startDiscardingStderr(command, this.projectDir);

    return Shell.Output.consumeStdout(
        process,
        reader -> reader.lines().map(LineCountReader::parseLine).filter(Objects::nonNull).toList());
  }

  private List<FileIndentationStats> readFileIndentation(final List<String> changedPaths) {
    val command =
        changedPaths != null
            ? IndentationReader.getCommand(this.commit, changedPaths)
            : IndentationReader.getCommand(this.commit);
    val process = Shell.startDiscardingStderr(command, this.projectDir);

    return Shell.Output.consumeStdout(
        process, reader -> IndentationReader.parseOutput(reader, this.indentSpec));
  }

  @WithSpan("Read commit details")
  public CommitDetails read() {
    Span.current().setAttribute("commit", this.commit);
    Span.current().setAttribute("project", this.projectDir.toString());
    try (val scope = StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())) {
      return switch (this.mode) {
        case HISTORY -> {
          val dateTask = scope.fork(this::readCommitDate);
          val changesTask = scope.fork(this::readFileChanges);
          val renameTask = scope.fork(this::readFileRenamings);
          scope.join();
          yield new CommitDetails(
              new CommitInfo(this.commit, dateTask.get()),
              changesTask.get(),
              renameTask.get(),
              List.of(),
              List.of());
        }
        case TREE_STATS -> {
          val changedPaths = this.allFiles ? null : readChangedFiles();
          val lineCountTask = scope.fork(() -> readFileLineCounts(changedPaths));
          val indentTask = scope.fork(() -> readFileIndentation(changedPaths));
          scope.join();
          yield new CommitDetails(
              new CommitInfo(this.commit, Instant.EPOCH),
              List.of(),
              List.of(),
              lineCountTask.get(),
              indentTask.get());
        }
      };
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProgramException("Read interrupted while fetching details", e);
    }
  }

  public record CommitDetails(
      CommitInfo commit,
      List<FileChanges> fileChanges,
      List<FileRenaming> fileRenamings,
      List<FileLineCount> fileLineCounts,
      List<FileIndentationStats> fileIndentations) {}

  public record CommitInfo(String sha1, Instant date) {}

  public record FileChanges(String filename, int additions, int deletions) {}

  public record FileRenaming(String from, String to) {}

  public record FileLineCount(String path, int lineCount) {}

  public record FileIndentationStats(
      String path, int minIndent, int maxIndent, double meanIndent, int medianIndent, int bumps) {}
}
