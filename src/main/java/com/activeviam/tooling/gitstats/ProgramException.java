/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

/**
 * @author ActiveViam
 */
public class ProgramException extends RuntimeException {

  public ProgramException(final String message, final Exception cause) {
    super(message, cause);
  }

  public ProgramException(final String message) {
    super(message);
  }

  public ProgramException(final Exception e) {
    super(e);
  }
}
