/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * @author ActiveViam
 */
@Command(
    name = "tree-stats",
    mixinStandardHelpOptions = true,
    description = "Extract tree statistics: line counts and indentation")
public class TreeStatsCommand implements Callable<Integer> {

  @Mixin SharedOptions options;

  @Option(
      names = {"-i", "--indent"},
      required = true,
      description = "Indent unit: <number><t|s> (e.g. 2t, 4s)")
  private String indent;

  @Override
  public Integer call() {
    val config =
        new Application.Config(
            options.projectDirectory,
            options.outputDirectory,
            options.branch,
            options.resolvedStartCommit(),
            options.count,
            Application.IndentSpec.parse(indent));
    val startTime = System.nanoTime();
    val program = new TreeStatsProgram(config);
    program.run();
    val endTime = System.nanoTime();
    Application.logger.info(
        "Execution time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + "s");
    return 0;
  }
}
