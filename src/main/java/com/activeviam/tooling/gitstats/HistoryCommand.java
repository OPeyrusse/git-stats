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

/**
 * @author ActiveViam
 */
@Command(
    name = "history",
    mixinStandardHelpOptions = true,
    description = "Extract history statistics: branches, commits, changes, renamings")
public class HistoryCommand implements Callable<Integer> {

  @Mixin SharedOptions options;

  @Override
  public Integer call() {
    val config =
        new Application.Config(
            options.projectDirectory,
            options.outputDirectory,
            options.branch,
            options.resolvedStartCommit(),
            options.count,
            null,
            false);
    val startTime = System.nanoTime();
    val program = new HistoryProgram(config);
    program.run();
    val endTime = System.nanoTime();
    Application.logger.info(
        "Execution time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + "s");
    return 0;
  }
}
