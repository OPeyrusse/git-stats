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
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitDetails;
import com.activeviam.tooling.gitstats.internal.orchestration.Action;
import com.activeviam.tooling.gitstats.internal.orchestration.BranchCsvWritePipeline;
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
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteChangesAction;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteCommits;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteRenamingAction;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
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

  public void run() {
    try {
      Files.createDirectories(this.config.outputDirectory());
    } catch (IOException e) {
      throw new ProgramException("Failed to create output directory", e);
    }

    try (var scope = StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())) {
      final var commitOutput = this.<String>queueOf(20);
      val branchCommitReader =
          new BranchCommitReader(this.config.projectDirectory(), this.config.branch(), this.config.startCommit(),
              this.config.count(), commitOutput);
      Threading.submit(scope, branchCommitReader::run);

      final var pipelineActions = this.<FetchCommit>queueOf(20);
      val commitTransformer =
          new ReadCommitPipeline(this.config.projectDirectory(), this.config.indentSpec(), commitOutput, pipelineActions);
      Threading.submit(scope, commitTransformer::run);

      val detailsQueue = new Queue<Action<CommitDetails>>(10);
      Threading.parallelize(
          scope,
          1,
          _ -> {
            val pipeline = new FetchCommitPipeline(pipelineActions, detailsQueue);
            return pipeline::run;
          });

      val changeQueue = new Queue<Action<WriteDispacher.WriteChangesAction>>(100);
      val renamingQueue = new Queue<Action<WriteDispacher.WriteRenamingAction>>(100);
      val commitQueue = new Queue<Action<WriteCommits>>(100);
      val writeDispatcher =
          new WriteDispacher(detailsQueue, changeQueue, renamingQueue, commitQueue);
      Threading.submit(scope, writeDispatcher::run);

      val commitWriteQueue = this.<WriteCommits>queueOf(100);
      val branchWriteQueue = this.<WriteCommits>queueOf(100);
      val commitMultiplex =
          new Multiplexer<>(commitQueue, List.of(commitWriteQueue, branchWriteQueue));
      Threading.submit(scope, commitMultiplex::run);

      if (false) {
        createParquetPipelines(scope, branchWriteQueue, commitWriteQueue, changeQueue, renamingQueue);
      } else {
        createCsvPipelines(scope, branchWriteQueue, commitWriteQueue, changeQueue, renamingQueue);
      }

      scope.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProgramException("Application execution interrupted", e);
    }
  }

  private void createParquetPipelines(
      final StructuredTaskScope<?, ?> scope,
      final Queue<Action<WriteCommits>> branchWriteQueue,
      final Queue<Action<WriteCommits>> commitWriteQueue,
      final Queue<Action<WriteChangesAction>> changeQueue,
      final Queue<Action<WriteRenamingAction>> renamingQueue) {
    val branchPipeline =
        new BranchWritePipeline(
            branchWriteQueue, this.config.outputDirectory(), "branches-%04d.parquet", this.config.branch());
    Threading.submit(scope, branchPipeline::run);

    val commitPipeline =
        new CommitWritePipeline(commitWriteQueue, this.config.outputDirectory(), "commits-%04d.parquet");
    Threading.submit(scope, commitPipeline::run);

    Threading.parallelize(scope, 1, i -> {
      val changePipeline =
          new ChangeWriterPipeline(changeQueue, this.config.outputDirectory(), "changes-" + i + "-%04d.parquet");
      return changePipeline::run;
    });
    Threading.parallelize(scope, 1, i -> {
      val renamingPipeline =
          new RenameWriterPipeline(renamingQueue, this.config.outputDirectory(),
              "renamings-" + i + "-%04d.parquet");
      return renamingPipeline::run;
    });
  }

  private void createCsvPipelines(
      final StructuredTaskScope<?, ?> scope,
      final Queue<Action<WriteCommits>> branchWriteQueue,
      final Queue<Action<WriteCommits>> commitWriteQueue,
      final Queue<Action<WriteChangesAction>> changeQueue,
      final Queue<Action<WriteRenamingAction>> renamingQueue) {
    val branchPipeline =
        new BranchCsvWritePipeline(
            branchWriteQueue, this.config.outputDirectory(), "branches-%04d.csv", this.config.branch());
    Threading.submit(scope, branchPipeline);

//    val commitPipeline =
//        new CommitCsvWritePipeline(commitWriteQueue, this.config.outputDirectory(), "commits-%04d.csv");
//    Threading.submit(scope, commitPipeline::run);
//
//    Threading.parallelize(scope, 4, i -> {
//      val changePipeline =
//          new ChangeCsvWriterPipeline(changeQueue, this.config.outputDirectory(), "changes-" + i + "-%04d.csv");
//      return changePipeline::run;
//    });
//    Threading.parallelize(scope, 2, i -> {
//      val renamingPipeline =
//          new RenameCsvWriterPipeline(renamingQueue, this.config.outputDirectory(),
//              "renamings-" + i + "-%04d.csv");
//      return renamingPipeline::run;
//    });
  }
}
