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
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * @author ActiveViam
 */
public class CommitCsvWritePipeline extends ACsvWritePipeline<WriteCommits> {

  public CommitCsvWritePipeline(
      Queue<Action<WriteCommits>> queue, Path outputDirectory, String filePattern) {
    super(queue, outputDirectory, filePattern, 1 << 22);
  }

  @Override
  protected void writeHeader(PrintWriter writer) {
    writer.println("commit,timestamp,date");
  }

  @Override
  protected int processCommand(WriteCommits command, PrintWriter writer) {
    for (final var commit : command.commits()) {
      writer.printf(
          "%s,%d,%s%n", commit.sha1(), commit.date().getEpochSecond(), getDate(commit.date()));
    }
    return command.commits().size();
  }

  private static String getDate(Instant commit) {
    return commit.atOffset(ZoneOffset.UTC).toLocalDate().toString();
  }
}
