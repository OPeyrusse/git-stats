/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import com.activeviam.tooling.gitstats.Application.Config;
import com.activeviam.tooling.gitstats.internal.Threading;
import com.activeviam.tooling.gitstats.internal.explorer.BranchCommitReader;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitDetails;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FetchMode;
import com.activeviam.tooling.gitstats.internal.orchestration.Action;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import com.activeviam.tooling.gitstats.internal.orchestration.IndentationCsvWriterPipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.LinesCsvWriterPipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.Queue;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteIndentationAction;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteLinesAction;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public class TreeStatsProgram {

  private static final int MAX_CONCURRENT_FETCHES = 20;

  private final Config config;

  private <T> Queue<Action<T>> queueOf(final int capacity) {
    return new Queue<>(capacity);
  }

  public void run() {
    try {
      Files.createDirectories(this.config.outputDirectory());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create output directory", e);
    }

    Threading.execute(
        scope -> {
          final var commitOutput = this.<String>queueOf(20);
          final var detailsOutput = this.<CommitDetails>queueOf(20);
          val branchCommitReader =
              new BranchCommitReader(
                  this.config.projectDirectory(),
                  this.config.branch(),
                  this.config.startCommit(),
                  this.config.count(),
                  commitOutput);
          Threading.submit(scope, branchCommitReader::run);
          Threading.submit(scope, () -> processCommits(commitOutput, detailsOutput));
          Threading.submit(scope, () -> processDetailsToCsv(detailsOutput));
        });
  }

  private void processCommits(
      final Queue<Action<String>> input, final Queue<Action<CommitDetails>> output) {
    final var semaphore = new Semaphore(MAX_CONCURRENT_FETCHES);
    Threading.execute(
        scope -> {
          String lastCommit = null;
          while (true) {
            val action = input.take();
            switch (action) {
              case Value(final var commit) -> {
                lastCommit = commit;
                semaphore.acquire();
                Threading.submit(
                    scope,
                    () -> {
                      try {
                        fetchCommit(output, commit);
                      } finally {
                        semaphore.release();
                      }
                    });
              }
              case Action.Stop<?> _ -> {
                Application.logger.info("Last commit: " + lastCommit);
                return;
              }
            }
          }
        });
    output.put(Action.stop());
  }

  private final Set<String> commits = Collections.synchronizedSet(new HashSet<>());

  private void fetchCommit(Queue<Action<CommitDetails>> output, String commit) {
    commits.add(commit);
    val reader =
        new ReadCommitDetails(
            this.config.projectDirectory(),
            commit,
            this.config.indentSpec(),
            FetchMode.TREE_STATS,
            this.config.allFiles());
    val details = reader.read();
    output.put(Action.value(details));
    commits.remove(commit);
  }

  private void processDetailsToCsv(final Queue<Action<CommitDetails>> input) {
    Threading.execute(
        scope -> {
          val linesQueue = new Queue<Action<WriteLinesAction>>(20);
          val linesWriter =
              new LinesCsvWriterPipeline(
                  linesQueue, this.config.outputDirectory(), "lines-%04d.csv");
          Threading.submit(scope, linesWriter);

          val indentQueue = new Queue<Action<WriteIndentationAction>>(20);
          val indentWriter =
              new IndentationCsvWriterPipeline(
                  indentQueue, this.config.outputDirectory(), "indentation-%04d.csv");
          Threading.submit(scope, indentWriter);

          Threading.submit(
              scope,
              () -> {
                while (true) {
                  final var action = input.take();
                  switch (action) {
                    case Value(final var details) -> {
                      linesQueue.put(new Value<>(new WriteLinesAction(List.of(details))));
                      indentQueue.put(new Value<>(new WriteIndentationAction(List.of(details))));
                    }
                    case Stop<?> _ -> {
                      linesQueue.put(Stop.create());
                      indentQueue.put(Stop.create());
                      return;
                    }
                  }
                }
              });
        });
  }
}
