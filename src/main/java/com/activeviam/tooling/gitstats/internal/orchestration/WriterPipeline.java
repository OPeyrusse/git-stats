/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class WriterPipeline<T> {

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final Queue<Action<T>> queue;
  private final Path outputDirectory;
  private final String filePattern;
  private int count = 0;

  @WithSpan("Write commit stream")
  protected final void doRun() {
    if (!this.started.compareAndSet(false, true)) {
      throw new IllegalStateException("Pipeline already started");
    }
    try {
    while (true) {
      final var action = this.queue.take();
      switch (action) {
        case Value(final var command) -> {
          val name = generateFileName();
          val writer = createWrite(command, this.outputDirectory.resolve(name));
          writer.run();
          Span.current().addEvent("Batch of values written");
        }
        case Stop<?> _ -> {
          return;
        }
      }
    }
    } finally {
      this.started.set(false);
    }
  }

  protected abstract Runnable createWrite(T command, Path outputFile);

  private String generateFileName() {
    return String.format(this.filePattern, this.count++);
  }
}
