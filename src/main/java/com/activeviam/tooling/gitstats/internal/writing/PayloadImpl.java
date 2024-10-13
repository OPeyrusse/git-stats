/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * @author ActiveViam
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PayloadImpl<U> implements Payload<U> {

  private final Supplier<Stream<U>> streamBuilder;

  public static <T, U> PayloadImpl<U> mapping(Collection<T> data, Function<T, U> mapper) {
    return new PayloadImpl<>(() -> data.stream().map(mapper));
  }

  public static <T, U> PayloadImpl<U> streaming(
      Collection<T> data, Function<Stream<T>, Stream<U>> mapper) {
    return new PayloadImpl<>(() -> mapper.apply(data.stream()));
  }

  @Override
  public Stream<U> toStream() {
    return streamBuilder.get();
  }
}
