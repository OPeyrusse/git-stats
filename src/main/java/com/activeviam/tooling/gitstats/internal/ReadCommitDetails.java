/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal;

import com.activeviam.tooling.gitstats.internal.Shell.Output;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    final var output =
        Shell.execute(
            List.of("git", "show", "--format=%ct", this.commit, "--no-patch"), this.projectDir);
    final var stdout = Output.readStream(output.stdout()).trim();
    return Instant.ofEpochMilli(Long.parseLong(stdout));
  }

  private List<FileChanges> readFileChanges() {
    final var output =
        Shell.execute(
            List.of("git", "show", "--format=oneline", "--numstat", this.commit), this.projectDir);

    try (final var reader = new BufferedReader(new InputStreamReader(output.stdout()))) {
      return reader.lines().skip(1).map(this::parseFileChange).collect(Collectors.toList());
    } catch (final IOException e) {
      throw new IllegalStateException("Cannot read git output for file changes", e);
    }
  }

  private FileChanges parseFileChange(final String line) {
    final var parts = line.split("\\s+");
    return new FileChanges(parts[2], parseCount(parts[0]), Integer.parseInt(parts[1]));
  }

  private static int parseCount(final String value) {
    if (value.contains("-")) {
      return -1;
    }
    return Integer.parseInt(value);
  }

  private List<FileRenaming> readFileRenamings() {
    final var output =
        Shell.execute(
            List.of("git", "show", "--format=oneline", "--numstat", this.commit), this.projectDir);

    try (final var reader = new BufferedReader(new InputStreamReader(output.stdout()))) {
      return reader
          .lines()
          .skip(1)
          .map(this::parseFileInfo)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new IllegalStateException("Cannot read git output for file changes", e);
    }
  }

  private FileRenaming parseFileInfo(final String line) {
    final var parts = line.split("\\s+");
    if (parts.length == 7) {
      return new FileRenaming(parts[6], parts[5]);
    } else {
      return null;
    }
  }

  @WithSpan("Read commit details")
  public CommitDetails read() {
    Span.current().setAttribute("commit", this.commit);
    Span.current().setAttribute("project", this.projectDir.toString());
    final Instant commitDate = readCommitDate();
    final var changes = readFileChanges();
    final var renamings = readFileRenamings();
    return new CommitDetails(this.commit, commitDate, changes, renamings);
  }

  public record CommitDetails(
      String commit,
      Instant date,
      List<FileChanges> fileChanges,
      List<FileRenaming> fileRenamings) {}

  public record FileChanges(String filename, int additions, int deletions) {}

  public record FileRenaming(String from, String to) {}
}
