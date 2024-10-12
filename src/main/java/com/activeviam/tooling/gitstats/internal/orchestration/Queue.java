/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author ActiveViam
 */
public class Queue<T> {

  private final LinkedBlockingQueue<T> inner;

  public Queue(int capacity) {
    this.inner = new LinkedBlockingQueue<>(capacity);
  }

  public Collection<T> values() {
    return inner;
  }

  public void put(T element) {
    try {
      inner.put(element);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while putting element", e);
    }
  }

  public T take() {
    try {
      return inner.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while taking element", e);
    }
  }
}
