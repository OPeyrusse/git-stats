/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.explorer;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * @author ActiveViam
 */
public class Shell {

  @WithSpan
  public static Output execute(
      final @SpanAttribute List<String> command, final @SpanAttribute Path workingDirectory) {
    // Execute the command
    final var builder = new ProcessBuilder(command);
    builder.directory(workingDirectory.toFile());
    builder.environment().put("LANG", "LC_ALL");
    Process process;
    try {
      process = builder.start();
      process.waitFor();
      if (process.exitValue() != 0) {
        throw new RuntimeException("Command failed with exit status " + process.exitValue());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Operation interrupted", e);
    }
    return new Output(process.getInputStream(), process.getErrorStream());
  }

  public record Output(InputStream stdout, InputStream stderr) {
    public static String readStream(final InputStream stream) {
      try {
        return new String(stream.readAllBytes());
      } catch (final IOException e) {
        throw new IllegalStateException("Cannot read output stream", e);
      }
    }
  }
}
