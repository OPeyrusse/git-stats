/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.explorer;

import com.activeviam.tooling.gitstats.ProgramException;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.val;

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
        if (logger.isLoggable(Level.SEVERE)) {
          logger.severe("Output " + Output.readStream(process.getInputStream()));
          logger.severe("Error " + Output.readStream(process.getErrorStream()));
        }
        throw new ProgramException(
            "Command " + command + " failed with exit status " + process.exitValue());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProgramException("Command " + command + " interrupted", e);
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
      throw new ProgramException("Failure in command " + command, e);
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

    public static <T> T consumeStdout(
        final Process process, Function<BufferedReader, T> transformer) {
      final T result;
      try (final var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        result = transformer.apply(reader);
      } catch (final IOException e) {
        throw new IllegalStateException("Cannot read git output for file changes", e);
      }
      checkProcessCompletion(process);
      return result;
    }

    private static void checkProcessCompletion(Process process) {
      try {
        val success = process.waitFor(5, TimeUnit.SECONDS);
        if (success) {
          if (process.exitValue() != 0) {
            throw new ProgramException("Process failed with exit code " + process.exitValue());
          }
        } else {
          throw new ProgramException("Process did not complete in time");
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ProgramException(
            "Interrupted while waiting for the process completion after ending", e);
      }
    }
  }
}
