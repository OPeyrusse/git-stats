/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author ActiveViam
 */
public class PayloadImpl<T, U> implements Payload<U> {

  private final Collection<T> data;
  private final Function<T, U> mapper;

  public PayloadImpl(Collection<T> data, Function<T, U> mapper) {
    this.data = data;
    this.mapper = mapper;
  }

  @Override
  public Stream<U> toStream() {
    return data.stream().map(mapper);
  }
}
