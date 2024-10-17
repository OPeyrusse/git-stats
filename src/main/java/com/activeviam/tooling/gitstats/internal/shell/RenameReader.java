/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.shell;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileRenaming;
import java.util.List;
import java.util.Optional;

/**
 * @author ActiveViam
 */
public class RenameReader {

  private RenameReader() {}

  public static List<String> getCommand(final String commit) {
    return List.of("git", "show", "--format=oneline", "--raw", commit);
  }

  public static Optional<FileRenaming> parseLine(final String line) {
    final var parts = line.split("\\s+");
    if (parts.length == 7) {
      return Optional.of(new FileRenaming(parts[6], parts[5]));
    } else {
      return Optional.empty();
    }
  }
}
