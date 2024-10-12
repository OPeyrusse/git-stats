/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import com.activeviam.tooling.gitstats.internal.Shell;
import com.activeviam.tooling.gitstats.internal.Shell.Output;
import java.nio.file.Path;
import java.util.List;
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

  public Application(final Path projectDirectory, final Path outputDirectory) {
    this.projectDirectory = projectDirectory;
    this.outputDirectory = outputDirectory;
  }

  public void run() {
    // Execute git command
    final var result =
        Shell.execute(List.of("git", "status", "--short", "--branch"), this.projectDirectory);
    System.out.println(Output.readStream(result.stdout()));
  }

  public static void main(final String[] args) {
    final var options = defineCli();
    final var app = buildApplication(args, options);
    app.run();
  }

  private static Options defineCli() {
    final var options = new Options();

    final var exportFolder = new Option("p", "project", true, "Project to scan");
    exportFolder.setRequired(true);
    options.addOption(exportFolder);

    final var packagesToScan = new Option("o", "output", true, "Output directory");
    packagesToScan.setRequired(true);
    options.addOption(packagesToScan);

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
        Path.of(cmd.getOptionValue("project")), Path.of(cmd.getOptionValue("output")));
  }
}
