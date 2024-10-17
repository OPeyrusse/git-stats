/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.shell;

import java.time.Instant;
import java.util.List;

/**
 * @author ActiveViam
 */
public class CommitDateReader {

  private CommitDateReader() {}

  public static List<String> getCommand(final String commit) {
    return List.of("git", "show", "--format=%ct", commit, "--no-patch");
  }

  public static Instant parseLine(final String line) {
    return Instant.ofEpochSecond(Long.parseLong(line.trim()));
  }
}
