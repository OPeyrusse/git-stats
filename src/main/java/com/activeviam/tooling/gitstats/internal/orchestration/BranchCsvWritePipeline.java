/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteCommits;
import java.io.PrintWriter;
import java.nio.file.Path;

/**
 * @author ActiveViam
 */
public final class BranchCsvWritePipeline extends ACsvWritePipeline<WriteCommits> {

  private final String branch;

  public BranchCsvWritePipeline(
      Queue<Action<WriteCommits>> queue,
      Path outputDirectory,
      String filePattern,
      final String branch) {
    super(queue, outputDirectory, filePattern, 1 << 22);
    this.branch = branch;
  }

  @Override
  protected void writeHeader(PrintWriter writer) {
    writer.println("branch,commit");
  }

  @Override
  protected int processCommand(WriteCommits command, PrintWriter writer) {
    for (final var commit : command.commits()) {
      writer.printf("%s,%s%n", this.branch, commit.sha1());
    }
    return command.commits().size();
  }
}
