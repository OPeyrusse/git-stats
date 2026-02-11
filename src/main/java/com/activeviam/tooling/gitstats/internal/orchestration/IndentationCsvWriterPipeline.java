/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteIndentationAction;
import java.io.PrintWriter;
import java.nio.file.Path;
import lombok.val;

/**
 * @author ActiveViam
 */
public class IndentationCsvWriterPipeline extends ACsvWritePipeline<WriteIndentationAction> {

  public IndentationCsvWriterPipeline(Queue<Action<WriteIndentationAction>> queue, Path outputDirectory,
      String filePattern) {
    super(queue, outputDirectory, filePattern, 1 << 16);
  }

  @Override
  protected void writeHeader(PrintWriter writer) {
    writer.println("commit,path,min_indent,max_indent,mean_indent,median_indent");
  }

  @Override
  protected int processCommand(WriteIndentationAction command, PrintWriter writer) {
    int lines = 0;
    for (val details : command.commits()) {
      for (val stats : details.fileIndentations()) {
        writer.printf(
            "%s,%s,%d,%d,%.2f,%d%n",
            details.commit().sha1(),
            stats.path(),
            stats.minIndent(),
            stats.maxIndent(),
            stats.meanIndent(),
            stats.medianIndent());
        lines++;
      }
    }
    return lines;
  }
}
