/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitDetails;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitInfo;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import java.util.List;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
public class WriteDispacher {

  private final Queue<Action<CommitDetails>> commitQueue;
  private final Queue<Action<WriteChangesAction>> changeQueue;
  private final Queue<Action<WriteRenamingAction>> renamingQueue;
  private final Queue<Action<WriteCommits>> branchQueue;

  public void run() {
    val changeAccumulator = Accumulator.create(2000, this.changeQueue, WriteChangesAction::new);
    val renamingAccumulator = Accumulator.create(1000, this.renamingQueue, WriteRenamingAction::new);
    val commitAccumulator = Accumulator.create(1000, this.branchQueue, WriteCommits::new);
    while (true) {
      final var action = this.commitQueue.take();
      switch (action) {
        case Value(final var details) -> {
          changeAccumulator.add(details, details.fileChanges().size());
          renamingAccumulator.add(details, details.fileRenamings().size());
          commitAccumulator.add(details.commit(), 1);
        }
        case Stop<?> _ -> {
          changeAccumulator.flush();
          renamingAccumulator.flush();
          commitAccumulator.flush();
          return;
        }
      }
    }
  }

  public record WriteChangesAction(List<CommitDetails> commits) {

  }

  public record WriteRenamingAction(List<CommitDetails> commits) {

  }

  public record WriteCommits(List<CommitInfo> commits) {

  }

  /**
   * @author ActiveViam
   */
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static class Accumulator<T, U> {

    private final Buffer<T> buffer;
    private final Queue<Action<U>> queue;
    private final Function<List<T>, U> actionBuilder;

    public static <T, U> Accumulator<T, U> create(final int limit, final Queue<Action<U>> queue,
        Function<List<T>, U> actionBuilder) {
      return new Accumulator<>(new Buffer<>(limit), queue, actionBuilder);
    }

    public void add(final T item, int size) {
      buffer.add(item, size);
      if (buffer.hasEnough()) {
        emitAction();
      }
    }

    private void emitAction() {
      this.queue.put(Action.value(this.actionBuilder.apply(buffer.drain())));
    }

    public void flush() {
      if (!this.buffer.isEmpty()) {
        emitAction();
      }
      this.queue.put(Action.stop());
    }
  }
}
