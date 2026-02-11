/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class Application {

  static final Logger logger = Logger.getLogger(Application.class.getName());

  private final Config config;

  public void run() {
    val startTime = System.nanoTime();
    val pipeline = /*
    new PipelineProgram(config);
    /*/ new StructuredProgram(config);
    // */
    pipeline.run();
    val endTime = System.nanoTime();
    logger.info("Execution time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + "s");
  }

  static void main(final String[] args) {
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

    final var startCommit = new Option("s", "start", true, "Start commit");
    startCommit.setRequired(false);
    options.addOption(startCommit);

    final var count = new Option("n", "count", true, "Number of commits to collect");
    count.setRequired(false);
    options.addOption(count);

    final var indent = new Option("i", "indent", true, "Indent unit: <number><t|s> (e.g. 2t, 4s)");
    indent.setRequired(true);
    options.addOption(indent);

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
      formatter.printHelp("Git scanner", options);
      System.exit(1);
      throw new IllegalStateException("Unreachable");
    }

    return new Application(
        new Config(
            Path.of(cmd.getOptionValue("project")),
            Path.of(cmd.getOptionValue("output")),
            cmd.getOptionValue("branch"),
            Optional.ofNullable(cmd.getOptionValue("start")).orElse(cmd.getOptionValue("branch")),
            Integer.parseInt(cmd.getOptionValue("count", "10")),
            IndentSpec.parse(cmd.getOptionValue("indent"))));
  }

  public record Config(
      Path projectDirectory, Path outputDirectory, String branch, String startCommit, int count,
      IndentSpec indentSpec) {

  }

  public record IndentSpec(int size, char type) {

    public static IndentSpec parse(String spec) {
      if (spec == null || spec.length() < 2) {
        throw new IllegalArgumentException("Invalid indent spec: " + spec);
      }
      char typeChar = spec.charAt(spec.length() - 1);
      if (typeChar != 't' && typeChar != 's') {
        throw new IllegalArgumentException(
            "Invalid indent type '" + typeChar + "', expected 't' (tab) or 's' (space)");
      }
      int size = Integer.parseInt(spec.substring(0, spec.length() - 1));
      if (size <= 0) {
        throw new IllegalArgumentException("Indent size must be positive: " + size);
      }
      return new IndentSpec(size, typeChar);
    }

    public String indentUnit() {
      char ch = type == 't' ? '\t' : ' ';
      return String.valueOf(ch).repeat(size);
    }
  }
}
