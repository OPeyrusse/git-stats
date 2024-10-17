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
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitInfo;
import com.activeviam.tooling.gitstats.internal.orchestration.Action;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import com.activeviam.tooling.gitstats.internal.orchestration.Buffer;
import com.activeviam.tooling.gitstats.internal.orchestration.Queue;
import com.activeviam.tooling.gitstats.internal.writing.BranchWriter;
import com.activeviam.tooling.gitstats.internal.writing.CommitWriter;
import com.activeviam.tooling.gitstats.internal.writing.FileChangeWriter;
import com.activeviam.tooling.gitstats.internal.writing.FileRenamingWriter;
import com.activeviam.tooling.gitstats.internal.writing.PayloadImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public class StructuredProgram {

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

    Threading.execute(scope -> {
      final var commitOutput = this.<String>queueOf(20);
      final var detailsOutput = this.<CommitDetails>queueOf(20);
      val branchCommitReader =
          new BranchCommitReader(this.config.projectDirectory(), this.config.branch(), this.config.startCommit(), this.config.count(), commitOutput);
      Threading.submit(scope, branchCommitReader::run);
      Threading.submit(scope, () -> processCommits(commitOutput,detailsOutput));
      Threading.submit(scope, () -> processDetails(detailsOutput));
    });
  }

  private void processCommits(final Queue<Action<String>> input, final Queue<Action<CommitDetails>> output) {
    Threading.execute(scope -> {
      String lastCommit = null;
      while (true) {
        val action = input.take();
        switch (action) {
          case Value(final var commit) -> {
            lastCommit = commit;
            Threading.submit(scope, () -> fetchCommit(output, commit));
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
    val reader = new ReadCommitDetails(this.config.projectDirectory(), commit);
    val details = reader.read();
    output.put(Action.value(details));
    commits.remove(commit);
  }

  private void processDetails(Queue<Action<CommitDetails>> input) {
    Threading.execute(scope -> {
      val counter = new AtomicInteger(0);
      val changeAccumulator = new Buffer<CommitDetails>(2000);
      val renamingAccumulator = new Buffer<CommitDetails>( 5000);
      val commitAccumulator = new Buffer<CommitInfo>(10000);
      while (true) {
        final var action = input.take();
        switch (action) {
          case Value(final var details) -> {
            processValue(scope, details, changeAccumulator, counter, renamingAccumulator, commitAccumulator);
          }
          case Stop<?> _ -> {
            flush(scope, changeAccumulator, counter, renamingAccumulator, commitAccumulator);
            return;
          }
        }
        }
    });

  }

  private void processValue(final StructuredTaskScope<?> scope, final CommitDetails details,
      final Buffer<CommitDetails> changeAccumulator, final AtomicInteger counter, final Buffer<CommitDetails> renamingAccumulator,
      final Buffer<CommitInfo> commitAccumulator) {
    changeAccumulator.add(details, details.fileChanges().size());
    if (changeAccumulator.hasEnough()) {
      Threading.submit(scope, () -> writeChanges(changeAccumulator.drain(), counter.incrementAndGet()));
    }
    renamingAccumulator.add(details, details.fileRenamings().size());
    if (renamingAccumulator.hasEnough()) {
      Threading.submit(scope, () -> writeRenamings(renamingAccumulator.drain(), counter.incrementAndGet()));
    }
    commitAccumulator.add(details.commit(), 1);
    if (commitAccumulator.hasEnough()) {
      val commits = commitAccumulator.drain();
      Threading.submit(scope, () -> writeCommits(commits, counter.incrementAndGet()));
      Threading.submit(scope, () -> writeBranch(commits, counter.incrementAndGet()));
    }
  }

  private void flush(final StructuredTaskScope<?> scope, final Buffer<CommitDetails> changeAccumulator,
      final AtomicInteger counter, final Buffer<CommitDetails> renamingAccumulator, final  Buffer<CommitInfo> commitAccumulator) {
    if (changeAccumulator.isNotEmpty()) {
      Threading.submit(scope, () -> writeChanges(changeAccumulator.drain(), counter.incrementAndGet()));
    }
    if (renamingAccumulator.isNotEmpty()) {
      Threading.submit(scope, () -> writeRenamings(renamingAccumulator.drain(), counter.incrementAndGet()));
    }
    if (commitAccumulator.isNotEmpty()) {
      val commits = commitAccumulator.drain();
      Threading.submit(scope, () -> writeCommits(commits, counter.incrementAndGet()));
      Threading.submit(scope, () -> writeBranch(commits, counter.incrementAndGet()));
    }
  }

  private void writeChanges(List<CommitDetails> values, int id) {
    val writer =new FileChangeWriter(
        PayloadImpl.streaming(values, s -> s.flatMap(
                        details ->
                            details.fileChanges().stream()
                                .map(change -> Map.entry(details.commit().sha1(), change)))), createFileName("changes-%04d.parquet", id));
    writer.write();
  }

  private Path createFileName(String template, int id) {
    return this.config.outputDirectory().resolve(String.format(template, id));
  }

  private void writeRenamings(List<CommitDetails> values, int id ) {
    val writer =new FileRenamingWriter(
        PayloadImpl.streaming(values, s -> s.flatMap(
            details ->
                details.fileRenamings().stream()
                    .map(change -> Map.entry(details.commit(), change)))),
        createFileName("renaming-%04d.parquet", id));
    writer.write();
  }

  private void writeCommits(List<CommitInfo> values, int id) {
    val writer  = new CommitWriter(
        createFileName("commits-%04d.parquet", id),
       PayloadImpl.mapping(values, Function.identity())
    );
    writer.write();
  }

  private void writeBranch(List<CommitInfo> values, int id) {
    val writer  = new BranchWriter(
        createFileName("branch-%04d.parquet", id),
        this.config.branch(),
        PayloadImpl.mapping(values, CommitInfo::sha1)
    );
    writer.write();
  }


}
