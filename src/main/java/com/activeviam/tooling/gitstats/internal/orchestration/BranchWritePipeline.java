/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitInfo;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteCommits;
import com.activeviam.tooling.gitstats.internal.writing.BranchWriter;
import com.activeviam.tooling.gitstats.internal.writing.PayloadImpl;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import lombok.val;

/**
 * @author ActiveViam
 */
public class BranchWritePipeline extends WriterPipeline<WriteCommits> {

  private final String branch;

  public BranchWritePipeline(
      Queue<Action<WriteCommits>> queue, Path outputDirectory, String filePattern, String branch) {
    super(queue, outputDirectory, filePattern);
    this.branch = branch;
  }

  @WithSpan("Stream branch commits to files")
  public void run() {
    doRun();
  }

  @Override
  protected Runnable createWrite(WriteCommits command, Path outputFile) {
    val writer =
        new BranchWriter(
            outputFile, this.branch, PayloadImpl.mapping(command.commits(), CommitInfo::sha1));
    return writer::write;
  }
}
