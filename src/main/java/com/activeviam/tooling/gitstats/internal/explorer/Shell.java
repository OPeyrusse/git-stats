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
import java.util.logging.Logger;

/**
 * @author ActiveViam
 */
public class Shell {

  private static final Logger logger = Logger.getLogger(Shell.class.getName());

  @WithSpan
  public static Output execute(
      final @SpanAttribute List<String> command, final @SpanAttribute Path workingDirectory) {
    final Process process;
    try {
      process = start(command, workingDirectory);
      process.waitFor();
      if (process.exitValue() != 0) {
        logger.severe("Output " + Output.readStream(process.getInputStream()));
        logger.severe("Error " + Output.readStream(process.getErrorStream()));
        throw new RuntimeException("Command "+command+" failed with exit status " + process.exitValue());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Command "+command+" interrupted", e);
    }
    return new Output(process.getInputStream(), process.getErrorStream());
  }

  public static Process start(final List<String> command, final Path workingDirectory) {
    final var builder = new ProcessBuilder(command);
    builder.directory(workingDirectory.toFile());
    builder.environment().put("LANG", "LC_ALL");
    try {
      return builder.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
