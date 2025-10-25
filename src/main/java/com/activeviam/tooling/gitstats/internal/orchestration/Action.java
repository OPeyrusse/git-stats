/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.orchestration.Action.Stop;
import com.activeviam.tooling.gitstats.internal.orchestration.Action.Value;
import java.util.stream.Stream;

/**
 * @author ActiveViam
 */
public sealed interface Action<T> permits Stop, Value {

  static <T> Action<T> value(T value) {
    return new Value<>(value);
  }

  @SuppressWarnings("unchecked")
  static <T> Action<T> stop() {
    return (Action<T>) Stop.INSTANCE;
  }

  Stream<T> unpack();

  record Value<T>(T value) implements Action<T> {

    @Override
    public Stream<T> unpack() {
      return Stream.of(value);
    }
  }

  record Stop<T>() implements Action<T> {

    private static final Stop<Object> INSTANCE = new Stop<>();

    @SuppressWarnings("unchecked")
    public static <T> Stop<T> create() {
      return (Stop<T>) INSTANCE;
    }

    @Override
    public Stream<T> unpack() {
      return Stream.empty();
    }
  }
}
