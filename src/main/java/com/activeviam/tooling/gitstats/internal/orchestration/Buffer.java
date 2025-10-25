/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public class Buffer<E> {

  private final List<E> underlying = new ArrayList<>();
  private final int limit;
  private int count = 0;

  public void add(final @NonNull E element, final int size) {
    underlying.add(element);
    this.count += size;
  }

  public boolean hasEnough() {
    return this.count >= this.limit;
  }

  public boolean isEmpty() {
    return this.underlying.isEmpty();
  }

  public boolean isNotEmpty() {
    return !this.underlying.isEmpty();
  }

  public List<E> drain() {
    val result = List.copyOf(this.underlying);
    this.underlying.clear();
    this.count = 0;
    return result;
  }
}
