/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.explorer;

import com.activeviam.tooling.gitstats.internal.orchestration.Action;
import com.activeviam.tooling.gitstats.internal.orchestration.Queue;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
@Log
public class BranchCommitReader {

  private final Path projectDir;
  private final String branch;
  private final String startCommit;
  private final int historySize;
  private final Queue<Action<String>> output;

  @Getter(lazy = true, value = lombok.AccessLevel.PRIVATE)
  private final String resolvedCommit = resolveStartCommit();

  @Getter(lazy = true, value = lombok.AccessLevel.PRIVATE)
  private final Set<String> commitsToIgnore = readCommitsToIgnore();

  public void run() {
    final var increment = 100;
    IntStream.iterate(0, i -> i < this.historySize, i -> i + increment)
        .forEach(start -> readCommits(start, increment));
    this.output.put(Action.stop());
  }

  @WithSpan("Read branch commits")
  private void readCommits(final int start, final int increment) {
    log.log(Level.INFO, "Reading commits from {0} to {1}", new Object[] {start, start + increment});
    Span.current().setAttribute("branch", this.branch);
    Span.current().setAttribute("start-commit", start);
    final var end = Math.min(start + increment, this.historySize);
    Span.current().setAttribute("end-commit", end);

    final var output =
        Shell.execute(
            List.of("git", "log", "--format=%H", commit(end) + ".." + commit(start)),
            this.projectDir);
    Shell.Output.readStream(output.stdout())
        .lines()
        .filter(Predicate.not(getCommitsToIgnore()::contains))
        .map(Action::value)
        .forEach(this.output::put);
  }

  private String commit(int index) {
    if (index == 0) {
      return getResolvedCommit();
    } else {
      return String.format("%s~%d", getResolvedCommit(), index);
    }
  }

  private String resolveStartCommit() {
    val output = Shell.execute(List.of("git", "rev-parse", this.startCommit), this.projectDir);
    return Shell.Output.readStream(output.stdout()).trim();
  }

  private Set<String> readCommitsToIgnore() {
    val ignoredFile = this.projectDir.resolve(".git-blame-ignore-revs");
    if (Files.exists(ignoredFile)) {
      try {
        return Files.readAllLines(ignoredFile).stream()
            .filter(Predicate.not(String::isBlank))
            .filter(line -> !line.startsWith("#"))
            .collect(Collectors.toUnmodifiableSet());
      } catch (final Exception e) {
        throw new RuntimeException("Failed to read ignored commits", e);
      }
    } else {
      return Set.of();
    }
  }
}
