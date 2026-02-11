/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteLinesAction;
import java.io.PrintWriter;
import java.nio.file.Path;
import lombok.val;

/**
 * @author ActiveViam
 */
public class LinesCsvWriterPipeline extends ACsvWritePipeline<WriteLinesAction> {

  public LinesCsvWriterPipeline(Queue<Action<WriteLinesAction>> queue, Path outputDirectory, String filePattern) {
    super(queue, outputDirectory, filePattern, 1 << 16);
  }

  @Override
  protected void writeHeader(PrintWriter writer) {
    writer.println("commit,path,lines");
  }

  @Override
  protected int processCommand(WriteLinesAction command, PrintWriter writer) {
    int lines = 0;
    for (val details : command.commits()) {
      for (val fileLineCount : details.fileLineCounts()) {
        writer.printf(
            "%s,%s,%d%n",
            details.commit().sha1(),
            fileLineCount.path(),
            fileLineCount.lineCount());
        lines++;
      }
    }
    return lines;
  }
}
