/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitDetails;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import com.activeviam.tooling.gitstats.internal.orchestration.CommitPipeline.FetchCommit;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public class WriterPipeline {

  private final Queue<Action<CommitDetails>> commitQueue;
  private final Queue<Action<WriteAction>> infoQueue;

  public void run() {
    while (true) {
      final var action = this.commitQueue.take();
      this.infoQueue.put(Action.stop());
    }
  }

  public record WriteAction(Path outputFile, String commit) {}
}
