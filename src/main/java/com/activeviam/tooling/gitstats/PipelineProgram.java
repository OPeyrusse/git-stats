/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import com.activeviam.tooling.gitstats.Application.Config;
import com.activeviam.tooling.gitstats.internal.explorer.BranchCommitReader;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitDetails;
import com.activeviam.tooling.gitstats.internal.orchestration.Action;
import com.activeviam.tooling.gitstats.internal.orchestration.BranchWritePipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.ChangeWriterPipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.CommitWritePipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.FetchCommitPipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.FetchCommitPipeline.FetchCommit;
import com.activeviam.tooling.gitstats.internal.orchestration.Multiplexer;
import com.activeviam.tooling.gitstats.internal.orchestration.Queue;
import com.activeviam.tooling.gitstats.internal.orchestration.ReadCommitPipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.RenameWriterPipeline;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteCommits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.IntFunction;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public class PipelineProgram {

  private final Config config;

  private <T> Queue<Action<T>> queueOf(final int capacity) {
    return new Queue<>(capacity);
  }

  private static void submit(final StructuredTaskScope<?> scope, final Runnable task) {
    scope.fork(
        () -> {
          try {
            task.run();
            return null;
          } catch (Exception e) {
            throw new RuntimeException("Failed to run task", e);
          }
        });
  }

  private static void parallelize(
      final StructuredTaskScope<?> scope, final int count, final IntFunction<Runnable> generator) {
    IntStream.range(0, count).mapToObj(generator::apply).forEach(action -> submit(scope, action));
  }

  public void run() {
    try {
      Files.createDirectories(this.config.outputDirectory());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create output directory", e);
    }

    try (var scope = new StructuredTaskScope<>()) {
      final var commitOutput = this.<String>queueOf(20);
      val branchCommitReader =
          new BranchCommitReader(this.config.outputDirectory(), this.config.branch(), this.config.startCommit(), this.config.count(), commitOutput);
      submit(scope, branchCommitReader::run);

      final var pipelineActions = this.<FetchCommit>queueOf(20);
      val commitTransformer =
          new ReadCommitPipeline(this.config.outputDirectory(), commitOutput, pipelineActions);
      submit(scope, commitTransformer::run);

      val detailsQueue = new Queue<Action<CommitDetails>>(10);
      parallelize(
          scope,
          2,
          _ -> {
            val pipeline = new FetchCommitPipeline(pipelineActions, detailsQueue);
            return pipeline::run;
          });

      val changeQueue = new Queue<Action<WriteDispacher.WriteChangesAction>>(100);
      val renamingQueue = new Queue<Action<WriteDispacher.WriteRenamingAction>>(100);
      val commitQueue = new Queue<Action<WriteCommits>>(100);
      val writeDispatcher =
          new WriteDispacher(detailsQueue, changeQueue, renamingQueue, commitQueue);
      submit(scope, writeDispatcher::run);

      val commitWriteQueue = this.<WriteCommits>queueOf(100);
      val branchWriteQueue = this.<WriteCommits>queueOf(100);
      val commitMultiplex =
          new Multiplexer<>(commitQueue, List.of(commitWriteQueue, branchWriteQueue));
      submit(scope, commitMultiplex::run);

      val branchPipeline =
          new BranchWritePipeline(
              branchWriteQueue, this.config.outputDirectory(), "branches-%04d.parquet", this.config.branch());
      submit(scope, branchPipeline::run);

      val commitPipeline =
          new CommitWritePipeline(commitWriteQueue, this.config.outputDirectory(), "commits-%04d.parquet");
      submit(scope, commitPipeline::run);

      parallelize(scope, 3, i -> {
        val changePipeline =
            new ChangeWriterPipeline(changeQueue, this.config.outputDirectory(), "changes-"+i+"-%04d.parquet");
        return changePipeline::run;
      });
      parallelize(scope, 3, i->{
        val renamingPipeline =
            new RenameWriterPipeline(renamingQueue, this.config.outputDirectory(), "renamings-"+i+"-%04d.parquet");
        return renamingPipeline::run;
      });

      scope.join();
    } catch (InterruptedException e) {
      throw new RuntimeException("Application execution interrupted", e);
    }
  }
}
