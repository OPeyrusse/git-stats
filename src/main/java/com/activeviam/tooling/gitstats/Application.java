/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

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
import java.util.logging.Logger;
import lombok.val;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * @author ActiveViam
 */
public class Application {

  private static final Logger logger = Logger.getLogger("git-tools");

  private final Path projectDirectory;
  private final Path outputDirectory;
  private final String branch;

  public Application(final Path projectDirectory, final Path outputDirectory, String branch) {
    this.projectDirectory = projectDirectory;
    this.outputDirectory = outputDirectory;
    this.branch = branch;
  }

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

  public void run() {
    try {
      Files.createDirectories(this.outputDirectory);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create output directory", e);
    }

    try (var scope = new StructuredTaskScope<>()) {
      final var commitOutput = this.<String>queueOf(20);
      val branchCommitReader =
          new BranchCommitReader(this.projectDirectory, this.branch, 10, commitOutput);
      submit(scope, branchCommitReader::run);

      final var pipelineActions = this.<FetchCommit>queueOf(20);
      val commitTransformer =
          new ReadCommitPipeline(this.projectDirectory, commitOutput, pipelineActions);
      submit(scope, commitTransformer::run);

      val detailsQueue = new Queue<Action<CommitDetails>>(10);
      val pipeline = new FetchCommitPipeline(pipelineActions, detailsQueue);
      submit(scope, pipeline::run);

      val changeQueue = new Queue<Action<WriteDispacher.WriteChangesAction>>(100);
      val renamingQueue = new Queue<Action<WriteDispacher.WriteRenamingAction>>(100);
      val commitQueue = new Queue<Action<WriteDispacher.WriteCommits>>(100);
      val infoOutput = this.<CommitDetails>queueOf(20);
      val writeDispatcher = new WriteDispacher(infoOutput, changeQueue, renamingQueue, commitQueue);
      submit(scope, writeDispatcher::run);

      val commitWriteQueue = this.<WriteCommits>queueOf(100);
      val branchWriteQueue = this.<WriteCommits>queueOf(100);
      val commitMultiplex =
          new Multiplexer<>(commitQueue, List.of(commitWriteQueue, branchWriteQueue));
      submit(scope, commitMultiplex::run);

      val branchPipeline =
          new BranchWritePipeline(
              branchWriteQueue, this.outputDirectory, "branches-%04d.parquet", this.branch);
      submit(scope, branchPipeline::run);

      val commitPipeline =
          new CommitWritePipeline(commitWriteQueue, this.outputDirectory, "commits-%04d.parquet");
      submit(scope, commitPipeline::run);

      val changePipeline =
          new ChangeWriterPipeline(changeQueue, this.outputDirectory, "changes-%04d.parquet");
      submit(scope, changePipeline::run);

      val renamingPipeline =
          new RenameWriterPipeline(renamingQueue, this.outputDirectory, "renamings-%04d.parquet");
      submit(scope, renamingPipeline::run);

      scope.join();
    } catch (InterruptedException e) {
      throw new RuntimeException("Application execution interrupted", e);
    }
  }

  public static void main(final String[] args) {
    final var options = defineCli();
    final var app = buildApplication(args, options);
    app.run();
  }

  private static Options defineCli() {
    final var options = new Options();

    final var projectDir = new Option("p", "project", true, "Project to scan");
    projectDir.setRequired(true);
    options.addOption(projectDir);

    final var outputPath = new Option("o", "output", true, "Output directory");
    outputPath.setRequired(true);
    options.addOption(outputPath);

    final var branch = new Option("b", "branch", true, "Branch to inspect");
    branch.setRequired(true);
    options.addOption(branch);

    return options;
  }

  private static Application buildApplication(final String[] args, final Options options) {
    final var parser = new DefaultParser();
    final var formatter = new HelpFormatter();
    final CommandLine cmd;
    try {
      cmd = parser.parse(options, args);
    } catch (final ParseException e) {
      logger.severe(e.getMessage());
      formatter.printHelp("API Mapper", options);
      System.exit(1);
      throw new IllegalStateException("Unreachable");
    }

    return new Application(
        Path.of(cmd.getOptionValue("project")),
        Path.of(cmd.getOptionValue("output")),
        cmd.getOptionValue("branch"));
  }
}
