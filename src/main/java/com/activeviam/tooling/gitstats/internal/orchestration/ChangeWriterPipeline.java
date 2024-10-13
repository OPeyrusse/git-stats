/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteChangesAction;
import com.activeviam.tooling.gitstats.internal.writing.FileChangeWriter;
import com.activeviam.tooling.gitstats.internal.writing.PayloadImpl;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.util.Map;
import lombok.val;

/**
 * @author ActiveViam
 */
public class ChangeWriterPipeline extends WriterPipeline<WriteChangesAction> {

  public ChangeWriterPipeline(
      Queue<Action<WriteChangesAction>> queue, Path outputDirectory, String filePattern) {
    super(queue, outputDirectory, filePattern);
  }

  @WithSpan("Write changes stream to files")
  public void run() {
    doRun();
  }

  @Override
  protected Runnable createWrite(WriteChangesAction command, Path outputFile) {
    val writer =
        new FileChangeWriter(
            PayloadImpl.streaming(
                command.commits(),
                s ->
                    s.flatMap(
                        details ->
                            details.fileChanges().stream()
                                .map(change -> Map.entry(details.commit().sha1(), change)))),
            outputFile);
    return writer::write;
  }
}
