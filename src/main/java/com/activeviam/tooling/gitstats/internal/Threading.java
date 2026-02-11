/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal;

import com.activeviam.tooling.gitstats.ProgramException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * @author ActiveViam
 */
public class Threading {

  public static void submit(final StructuredTaskScope<?, ?> scope, final Runnable task) {
    scope.fork(
        () -> {
          try {
            task.run();
            return null;
          } catch (Exception e) {
            throw new ProgramException("Failed to run task", e);
          }
        });
  }

  public static void parallelize(
      final StructuredTaskScope<?, ?> scope,
      final int count,
      final IntFunction<Runnable> generator) {
    IntStream.range(0, count).mapToObj(generator::apply).forEach(action -> submit(scope, action));
  }

  public static void execute(final ThrowingConsumer<StructuredTaskScope<?, ?>> action) {
    try (var scope = StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())) {
      action.accept(scope);
      scope.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProgramException("Step interrupted", e);
    } catch (Exception e) {
      throw new ProgramException("Step failed", e);
    }
  }

  public interface ThrowingConsumer<T> extends Consumer<T> {

    default void accept(T t) {
      try {
        acceptThrows(t);
      } catch (final Exception e) {
        throw new ProgramException(e);
      }
    }

    void acceptThrows(T t) throws Exception;
  }
}
