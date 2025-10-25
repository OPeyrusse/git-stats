/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.explorer;

import com.activeviam.tooling.gitstats.ProgramException;
import com.activeviam.tooling.gitstats.internal.explorer.Shell.Output;
import com.activeviam.tooling.gitstats.internal.orchestration.Action;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import com.activeviam.tooling.gitstats.internal.orchestration.Queue;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
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
  private final String lastCommit = "not-a-sha1";
  private final int historySize;
  private final Queue<Action<String>> output;
  private final AtomicBoolean stop = new AtomicBoolean(false);

  @Getter(lazy = true, value = lombok.AccessLevel.PRIVATE)
  private final String resolvedCommit = resolveStartCommit();

  @Getter(lazy = true, value = lombok.AccessLevel.PRIVATE)
  private final Set<String> commitsToIgnore = readCommitsToIgnore();

  public void run() {
    final var increment = 100;
    String startCommit = getResolvedCommit();
    for (int i = 0; i < this.historySize; i += increment) {
      final var commits = this.readCommits(startCommit, increment);
      final var filteredCommits = commits.stream()
          .takeWhile(commit -> !commit.equals(this.lastCommit))
          .toList();
      filteredCommits.stream().map(Value::new).forEach(this.output::put);

      if (filteredCommits.size() < commits.size()) {
        break; // We have reached the end of the iteration
      }
      startCommit = filteredCommits.getLast();
    }
    this.output.put(Action.stop());
  }

  @WithSpan("Read branch commits")
  private List<String> readCommits(final String startCommit, final int increment) {
    log.log(Level.INFO, "Reading commits from {0} for #{1}", new Object[]{startCommit, increment});
    Span.current().setAttribute("branch", this.branch);
    Span.current().setAttribute("start-commit", startCommit);

    final var commandOutput =
        Shell.execute(List.of("git", "rev-list", startCommit, "-n", String.valueOf(increment)),
            this.projectDir);
    final var commits =
        Output.readStream(commandOutput.stdout())
            .lines()
            .filter(Predicate.not(getCommitsToIgnore()::contains))
            .toList();
    Span.current().setAttribute("end-commit", commits.getLast());
    return commits;
  }

  private String resolveStartCommit() {
    val commandOutput =
        Shell.execute(List.of("git", "rev-parse", this.startCommit), this.projectDir);
    return Shell.Output.readStream(commandOutput.stdout()).trim();
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
        throw new ProgramException("Failed to read ignored commits", e);
      }
    } else {
      return Set.of();
    }
  }
}
