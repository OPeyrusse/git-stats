/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitDetails;
import java.nio.file.Path;

/**
 * @author ActiveViam
 */
public class Pipeline {

  private final Queue<CommitAction> commitQueue;
  private final Queue<CommitDetails> infoQueue;

  public Pipeline(final Queue<CommitAction> commitQueue, final Queue<CommitDetails> infoQueue) {
    this.commitQueue = commitQueue;
    this.infoQueue = infoQueue;
  }

  public void run() {
    while (true) {
        final var action = this.commitQueue.take();
        switch (action) {
          case FetchCommit(final var gitDir, final var commit) -> {
            final var infoReader = new ReadCommitDetails(gitDir, commit);
            final var info = infoReader.read();
            this.infoQueue.put(info);
          }
          case EndAction _ -> {return;}
        }
        }
  }

  public sealed interface CommitAction permits FetchCommit, EndAction {}

  public record FetchCommit(Path gitDir, String commit) implements CommitAction {}

  public record EndAction() implements CommitAction {}
}
