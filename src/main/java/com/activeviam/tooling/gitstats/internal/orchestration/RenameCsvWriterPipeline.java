/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteRenamingAction;
import java.io.PrintWriter;
import java.nio.file.Path;
import lombok.val;

/**
 * @author ActiveViam
 */
public class RenameCsvWriterPipeline extends ACsvWritePipeline<WriteRenamingAction> {

  public RenameCsvWriterPipeline(
      Queue<Action<WriteRenamingAction>> queue, Path outputDirectory, String filePattern) {
    super(queue, outputDirectory, filePattern, 1 << 18);
  }

  @Override
  protected void writeHeader(PrintWriter writer) {
    writer.println("commit,before,after");
  }

  @Override
  protected int processCommand(WriteRenamingAction command, PrintWriter writer) {
    int lines = 0;
    for (val details : command.commits()) {
      for (val change : details.fileRenamings()) {
        writer.printf("%s,%s,%s%n", details.commit().sha1(), change.from(), change.to());
        lines++;
      }
    }
    return lines;
  }
}
