/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * @author ActiveViam
 */
@Command(
    name = "git-stats",
    mixinStandardHelpOptions = true,
    description = "Extract statistics from git repositories")
public class Application implements Callable<Integer> {

  static final Logger logger = Logger.getLogger(Application.class.getName());

  @Option(
      names = {"-p", "--project"},
      required = true,
      description = "Project to scan")
  private Path projectDirectory;

  @Option(
      names = {"-o", "--output"},
      required = true,
      description = "Output directory")
  private Path outputDirectory;

  @Option(
      names = {"-b", "--branch"},
      required = true,
      description = "Branch to inspect")
  private String branch;

  @Option(
      names = {"-s", "--start"},
      description = "Start commit")
  private String startCommit;

  @Option(
      names = {"-n", "--count"},
      defaultValue = "10",
      description = "Number of commits to collect")
  private int count;

  @Option(
      names = {"-i", "--indent"},
      required = true,
      description = "Indent unit: <number><t|s> (e.g. 2t, 4s)")
  private String indent;

  @Override
  public Integer call() {
    val config =
        new Config(
            projectDirectory,
            outputDirectory,
            branch,
            startCommit != null ? startCommit : branch,
            count,
            IndentSpec.parse(indent));
    val startTime = System.nanoTime();
    val pipeline = new StructuredProgram(config);
    pipeline.run();
    val endTime = System.nanoTime();
    logger.info("Execution time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + "s");
    return 0;
  }

  public static void main(final String[] args) {
    System.exit(new CommandLine(new Application()).execute(args));
  }

  public record Config(
      Path projectDirectory,
      Path outputDirectory,
      String branch,
      String startCommit,
      int count,
      IndentSpec indentSpec) {}

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
