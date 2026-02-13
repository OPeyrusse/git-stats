/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.shell;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileLineCount;
import java.io.IOException;
import java.util.List;
import lombok.Getter;

/**
 * @author ActiveViam
 */
public class LineCountReader {

  @Getter(lazy = true)
  private static final String emptyTreeHash = computeEmptyTreeHash();

  private LineCountReader() {}

  private static String computeEmptyTreeHash() {
    try {
      final var process =
          new ProcessBuilder("git", "hash-object", "-t", "tree", "--stdin")
              .redirectErrorStream(true)
              .start();
      process.getOutputStream().close();
      final var hash = new String(process.getInputStream().readAllBytes()).trim();
      process.waitFor();
      return hash;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to compute empty tree hash", e);
    }
  }

  public static List<String> getCommand(final String commit) {
    return List.of("git", "diff", "--numstat", getEmptyTreeHash(), commit);
  }

  public static List<String> getCommand(final String commit, final List<String> paths) {
    final var command = new java.util.ArrayList<>(getCommand(commit));
    command.add("--");
    command.addAll(paths);
    return command;
  }

  public static FileLineCount parseLine(final String line) {
    if (line.isBlank()) {
      return null;
    }
    final var parts = line.split("\\t");
    if (parts.length < 3) {
      return null;
    }
    final var path = parts[2];
    if (!path.endsWith(".java")) {
      return null;
    }
    if (parts[0].equals("-")) {
      return null;
    }
    return new FileLineCount(path, Integer.parseInt(parts[0]));
  }
}
