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
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public class CommitPipeline {

  private final Queue<Action<FetchCommit>> commitQueue;
  private final Queue<Action<CommitDetails>> infoQueue;

  public void run() {
    while (true) {
        final var action = this.commitQueue.take();
        switch (action) {
          case Value(FetchCommit(final var gitDir, final var commit)) -> {
            final var infoReader = new ReadCommitDetails(gitDir, commit);
            final var info = infoReader.read();
            this.infoQueue.put(Action.value(info));
          }
          case Stop<?> _ -> {
            this.infoQueue.put(Action.stop());
            return;
        }
        }
        }
  }

  public record FetchCommit(Path gitDir, String commit)  {}
}
