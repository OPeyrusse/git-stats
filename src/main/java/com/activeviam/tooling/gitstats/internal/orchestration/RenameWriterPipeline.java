/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitDetails;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileRenaming;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteRenamingAction;
import com.activeviam.tooling.gitstats.internal.writing.FileRenamingWriter;
import com.activeviam.tooling.gitstats.internal.writing.PayloadImpl;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.util.Map;
import lombok.val;

/**
 * @author ActiveViam
 */
public class RenameWriterPipeline extends WriterPipeline<WriteRenamingAction> {

  public RenameWriterPipeline(
      Queue<Action<WriteRenamingAction>> queue, Path outputDirectory, String filePattern) {
    super(queue, outputDirectory, filePattern);
  }

  @WithSpan("Write changes stream to files")
  public void run() {
    doRun();
  }

  @Override
  protected Runnable createWrite(WriteRenamingAction command, Path outputFile) {
    val writer =
        new FileRenamingWriter(
            PayloadImpl.streaming(
                command.commits(),
                s ->
                    s.flatMap(
                        (CommitDetails details) ->
                            details.fileRenamings().stream()
                                .map(
                                    (FileRenaming change) -> Map.entry(details.commit(), change)))),
            outputFile);
    return writer::write;
  }
}
