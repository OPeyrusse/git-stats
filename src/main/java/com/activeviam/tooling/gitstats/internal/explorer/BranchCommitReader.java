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
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.stream.IntStream;

/**
 * @author ActiveViam
 */
public class BranchCommitReader {

  private final Path projectDir;
  private final String branch;
  private final int historySize;
  private final Queue<Action<String>> output;

  public BranchCommitReader(
      Path projectDir, String branch, int historySize, Queue<Action<String>> output) {
    this.projectDir = projectDir;
    this.branch = branch;
    this.historySize = historySize;
    this.output = output;
  }

  public void run() {
    final var increment = 100;
    IntStream.iterate(0, i -> i < this.historySize, i -> i + increment)
        .forEach(start -> readCommits(start, increment));
  }

  @WithSpan("Read branch commits")
  private void readCommits(final int start, final int increment) {
    Span.current().setAttribute("branch", this.branch);
    Span.current().setAttribute("start-commit", start);
    final var end = Math.min(start + increment, this.historySize);
    Span.current().setAttribute("end-commit", end);

    final var output =
        Shell.execute(List.of("git", "cherry", commit(end), commit(start)), this.projectDir);
    Shell.Output.readStream(output.stdout())
        .lines()
        .map(this::trimCommitLine)
        .map(Action::value)
        .forEach(this.output::put);
    this.output.put(Action.stop());
  }

  private String commit(int index) {
    return MessageFormat.format("{0}~{1}", this.branch, index);
  }

  private String trimCommitLine(final String line) {
    return line.replaceFirst("\\s*\\+\\s*", "");
  }
}
