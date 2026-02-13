/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import java.nio.file.Path;
import java.util.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * @author ActiveViam
 */
@Command(
    name = "git-stats",
    mixinStandardHelpOptions = true,
    description = "Extract statistics from git repositories",
    subcommands = {HistoryCommand.class, TreeStatsCommand.class})
public class Application implements Runnable {

  static final Logger logger = Logger.getLogger(Application.class.getName());

  @Override
  public void run() {
    new CommandLine(this).usage(System.out);
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
      IndentSpec indentSpec,
      boolean allFiles) {}

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
