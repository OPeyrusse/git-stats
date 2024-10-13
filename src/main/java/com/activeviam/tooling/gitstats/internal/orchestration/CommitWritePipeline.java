/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteCommits;
import com.activeviam.tooling.gitstats.internal.writing.CommitWriter;
import com.activeviam.tooling.gitstats.internal.writing.PayloadImpl;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.util.function.Function;
import lombok.val;

/**
 * @author ActiveViam
 */
public class CommitWritePipeline extends WriterPipeline<WriteCommits> {

  public CommitWritePipeline(
      Queue<Action<WriteCommits>> queue, Path outputDirectory, String filePattern) {
    super(queue, outputDirectory, filePattern);
  }

  @WithSpan("Stream commit details to files")
  public void run() {
    doRun();
  }

  @Override
  protected Runnable createWrite(WriteCommits command, Path outputFile) {
    val writer =
        new CommitWriter(outputFile, PayloadImpl.mapping(command.commits(), Function.identity()));
    return writer::write;
  }
}
