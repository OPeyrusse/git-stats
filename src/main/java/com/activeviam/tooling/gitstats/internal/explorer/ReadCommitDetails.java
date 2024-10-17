/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.explorer;

import com.activeviam.tooling.gitstats.internal.explorer.Shell.Output;
import com.activeviam.tooling.gitstats.internal.shell.ChangeReader;
import com.activeviam.tooling.gitstats.internal.shell.CommitDateReader;
import com.activeviam.tooling.gitstats.internal.shell.RenameReader;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
                .collect(Collectors.toList()));
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

  @WithSpan("Read commit details")
  public CommitDetails read() {
    Span.current().setAttribute("commit", this.commit);
    Span.current().setAttribute("project", this.projectDir.toString());
    try (val scope = new StructuredTaskScope.ShutdownOnFailure()) {
      val dateTask = scope.fork(this::readCommitDate);
      val changesTask = scope.fork(this::readFileChanges);
      val renameTask = scope.fork(this::readFileRenamings);

      scope.join();
      return new CommitDetails(
          new CommitInfo(this.commit, dateTask.get()), changesTask.get(), renameTask.get());
    } catch (final InterruptedException e) {
      throw new RuntimeException("Read interrupted while fetching details", e);
    }
  }

  public record CommitDetails(
      CommitInfo commit, List<FileChanges> fileChanges, List<FileRenaming> fileRenamings) {}

  public record CommitInfo(String sha1, Instant date) {}

  public record FileChanges(String filename, int additions, int deletions) {}

  public record FileRenaming(String from, String to) {}
}
