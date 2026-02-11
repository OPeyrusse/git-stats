/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public final class Multiplexer<T> {

  private final Queue<Action<T>> source;
  private final List<Queue<Action<T>>> targets;

  public void run() {
    while (true) {
      final var action = this.source.take();
      forward(action);
      switch (action) {
        case Value<?> _ -> {
          // Continue
        }
        case Stop<?> _ -> {
          forward(action);
          return;
        }
      }
    }
  }

  private void forward(final Action<T> action) {
    for (Queue<Action<T>> target : this.targets) {
      target.put(action);
    }
  }
}
