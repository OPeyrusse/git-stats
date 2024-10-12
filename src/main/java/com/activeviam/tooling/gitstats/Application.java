/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import com.activeviam.tooling.gitstats.internal.BranchCommitReader;
import com.activeviam.tooling.gitstats.internal.ReadCommitDetails;
import com.activeviam.tooling.gitstats.internal.writing.BranchWriter;
import com.activeviam.tooling.gitstats.internal.writing.FileChangeWriter;
import com.activeviam.tooling.gitstats.internal.writing.PayloadImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.logging.Logger;
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

  public void run() {
    // Execute git command
    final var output = new LinkedBlockingQueue<String>(20);
    final var commitReader = new BranchCommitReader(this.projectDirectory, this.branch, 10, output);
    commitReader.run();
    System.out.println("Commits read: " + List.copyOf(output));
    final var infoReader = new ReadCommitDetails(this.projectDirectory, output.peek());
    final var info = infoReader.read();
    System.out.println("First commit info");
    System.out.println("Date: " + info.date());

    // Write to output
    try {
      Files.createDirectories(this.outputDirectory);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create output directory", e);
    }

    final var branchWriter =
        new BranchWriter(
            this.outputDirectory.resolve("branch.parquet"),
            this.branch,
            new PayloadImpl<>(List.copyOf(output), Function.identity()));
    branchWriter.write();

    final var commitWriter =
        new FileChangeWriter(
            this.branch,
            new PayloadImpl<>(List.copyOf(info.fileChanges()), Function.identity()),
            this.outputDirectory.resolve("files.parquet"));
    commitWriter.write();
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
