/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.explorer;

import com.activeviam.tooling.gitstats.ProgramException;
import com.activeviam.tooling.gitstats.internal.explorer.Shell.Output;
import com.activeviam.tooling.gitstats.internal.shell.ChangeReader;
import com.activeviam.tooling.gitstats.internal.shell.CommitDateReader;
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

  private final Path projectDir;
  private final String commit;

  public ReadCommitDetails(final Path projectDir, final String commit) {
    this.projectDir = projectDir;
    this.commit = commit;
  }

  private Instant readCommitDate() {
    final var output = Shell.execute(CommitDateReader.getCommand(this.commit), this.projectDir);
    final var stdout = Output.readStream(output.stdout()).trim();
    return CommitDateReader.parseLine(stdout);
  }

  private List<FileChanges> readFileChanges() {
    final var process = Shell.start(ChangeReader.getCommand(this.commit), this.projectDir);

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
    val process = Shell.start(RenameReader.getCommand(this.commit), this.projectDir);

    return Shell.Output.consumeStdout(
        process,
        reader ->
            reader.lines().skip(1).map(this::parseFileInfo).filter(Objects::nonNull).toList());
  }

  private FileRenaming parseFileInfo(final String line) {
    return RenameReader.parseLine(line).orElse(null);
  }

  private List<FileLineCount> readFileLineCounts() {
    val process = Shell.start(LineCountReader.getCommand(this.commit), this.projectDir);

    return Shell.Output.consumeStdout(
        process,
        reader ->
            reader
                .lines()
                .map(LineCountReader::parseLine)
                .filter(Objects::nonNull)
                .toList());
  }

  @WithSpan("Read commit details")
  public CommitDetails read() {
    Span.current().setAttribute("commit", this.commit);
    Span.current().setAttribute("project", this.projectDir.toString());
    try (val scope = StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())) {
      val dateTask = scope.fork(this::readCommitDate);
      val changesTask = scope.fork(this::readFileChanges);
      val renameTask = scope.fork(this::readFileRenamings);
      val lineCountTask = scope.fork(this::readFileLineCounts);

      scope.join();
      return new CommitDetails(
          new CommitInfo(this.commit, dateTask.get()), changesTask.get(), renameTask.get(),
          lineCountTask.get());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProgramException("Read interrupted while fetching details", e);
    }
  }

  public record CommitDetails(
      CommitInfo commit, List<FileChanges> fileChanges, List<FileRenaming> fileRenamings,
      List<FileLineCount> fileLineCounts) {

  }

  public record CommitInfo(String sha1, Instant date) {

  }

  public record FileChanges(String filename, int additions, int deletions) {

  }

  public record FileRenaming(String from, String to) {

  }

  public record FileLineCount(String path, int lineCount) {

  }
}
