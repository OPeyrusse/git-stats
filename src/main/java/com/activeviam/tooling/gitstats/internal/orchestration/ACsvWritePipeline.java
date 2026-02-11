/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor
abstract class ACsvWritePipeline<T> implements Runnable {

  private final Queue<Action<T>> queue;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final Path outputDirectory;
  private final String filePattern;
  private final int limit;
  private int count = 0;
  private int lines;
  private PrintWriter writer;

  @WithSpan("Write commit stream")
  public final void run() {
    if (!this.started.compareAndSet(false, true)) {
      throw new IllegalStateException("Pipeline already started");
    }
    try {
      while (true) {
        final var action = this.queue.take();
        switch (action) {
          case Value(final var command) -> {
            if (this.lines >= this.limit || this.writer == null) {
              if (this.writer != null) {
                this.writer.close();
              }
              this.writer = createNewWrite();
              this.lines = 0;
            }
            val writtenLines = processCommand(command, this.writer);
            this.lines += writtenLines;
            Span.current().addEvent("Batch of values written");
          }
          case Action.Stop<?> _ -> {
            return;
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not write data", e);
    } finally {
      if (this.writer != null) {
        this.writer.close();
      }
      this.started.set(false);
    }
  }

  private PrintWriter createNewWrite() throws IOException {
    val name = generateFileName();
    final var targetFile = this.outputDirectory.resolve(name);
    val instance = new PrintWriter(Files.newBufferedWriter(targetFile));
    writeHeader(instance);
    return instance;
  }

  protected abstract void writeHeader(PrintWriter writer);

  protected abstract int processCommand(T command, PrintWriter writer);

  private String generateFileName() {
    return String.format(this.filePattern, this.count++);
  }
}
