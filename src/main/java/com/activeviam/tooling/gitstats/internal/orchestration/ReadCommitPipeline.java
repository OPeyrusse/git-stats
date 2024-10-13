/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import com.activeviam.tooling.gitstats.internal.orchestration.FetchCommitPipeline.FetchCommit;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public class ReadCommitPipeline {

  private final Path projectDirectory;
  private final Queue<Action<String>> source;
  private final Queue<Action<FetchCommit>> target;

  public void run() {
    while (true) {
      final var action = this.source.take();
      switch (action) {
        case Value(final var commit) -> this.target.put(Action.value(new FetchCommit(this.projectDirectory, commit)));
        case Stop<?> _ -> {
          this.target.put(Action.stop());
          return;
      }
    }
  }
    }

}
