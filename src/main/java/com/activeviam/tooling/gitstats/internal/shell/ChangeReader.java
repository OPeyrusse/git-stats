/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.shell;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileChanges;
import java.util.List;
import java.util.regex.Pattern;
import lombok.val;

/**
 * @author ActiveViam
 */
public class ChangeReader {

  private static final Pattern COMPLEX_FILE_PATTERN =
      Pattern.compile(
          "^(?<adds>-|\\d+)\\s+(?<dels>-|\\d+)\\s+(?<before>.*?)\\{.*? => (?<target>.*?)}(?<after>.*)$");

  private ChangeReader() {}

  public static List<String> getCommand(final String commit) {
    return List.of("git", "show", "--format=oneline", "--numstat", commit);
  }

  public static FileChanges parseLine(final String line) {
    if (line.contains("=>")) {
      return parseComplexLine(line);
    } else {
      return parseSimpleLine(line);
    }
  }

  private static FileChanges parseComplexLine(final String line) {
    val matcher = COMPLEX_FILE_PATTERN.matcher(line);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Cannot parse line: " + line);
    }
    val adds = matcher.group("adds");
    val dels = matcher.group("dels");
    val before = matcher.group("before");
    val target = matcher.group("target");
    val after = matcher.group("after");
    return new FileChanges(before + target + after, parseCount(adds), parseCount(dels));
  }

  private static FileChanges parseSimpleLine(final String line) {
    final var parts = line.split("\\s+");
    return new FileChanges(parts[2], parseCount(parts[0]), parseCount(parts[1]));
  }

  private static int parseCount(final String value) {
    if (value.contains("-")) {
      return -1;
    }
    return Integer.parseInt(value);
  }
}
