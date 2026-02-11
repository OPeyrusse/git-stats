/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.shell;

import com.activeviam.tooling.gitstats.Application.IndentSpec;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileIndentationStats;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ActiveViam
 */
public class IndentationReader {

  private IndentationReader() {}

  public static List<String> getCommand(final String commit) {
    return List.of("git", "diff", LineCountReader.getEmptyTreeHash(), commit);
  }

  public static List<FileIndentationStats> parseOutput(final BufferedReader reader, final IndentSpec indentSpec) {
    final var results = new ArrayList<FileIndentationStats>();
    final var indentUnit = indentSpec.indentUnit();
    String currentFile = null;
    var indentLevels = new ArrayList<Integer>();

    for (final var line : (Iterable<String>) reader.lines()::iterator) {
      if (line.startsWith("+++ b/")) {
        // Flush previous file
        if (currentFile != null && !indentLevels.isEmpty()) {
          results.add(computeStats(currentFile, indentLevels));
        }
        currentFile = line.substring("+++ b/".length());
        indentLevels = new ArrayList<>();
        if (!currentFile.endsWith(".java")) {
          currentFile = null;
        }
        continue;
      }

      if (currentFile == null) {
        continue;
      }

      // Only process added lines (skip diff headers, context, removals)
      if (!line.startsWith("+") || line.startsWith("+++")) {
        continue;
      }

      // Remove the leading '+' from the diff
      final var content = line.substring(1);

      // Skip blank lines
      if (content.isBlank()) {
        continue;
      }

      indentLevels.add(countIndentLevels(content, indentUnit));
    }

    // Flush last file
    if (currentFile != null && !indentLevels.isEmpty()) {
      results.add(computeStats(currentFile, indentLevels));
    }

    return results;
  }

  static int countIndentLevels(final String line, final String indentUnit) {
    int levels = 0;
    int pos = 0;
    while (pos + indentUnit.length() <= line.length()
        && line.startsWith(indentUnit, pos)) {
      levels++;
      pos += indentUnit.length();
    }
    return levels;
  }

  static FileIndentationStats computeStats(final String path, final List<Integer> levels) {
    final var sorted = new ArrayList<>(levels);
    Collections.sort(sorted);
    final int min = sorted.getFirst();
    final int max = sorted.getLast();
    final double mean = sorted.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    final int median = computeMedian(sorted);
    return new FileIndentationStats(path, min, max, mean, median);
  }

  static int computeMedian(final List<Integer> sorted) {
    final int size = sorted.size();
    if (size % 2 == 1) {
      return sorted.get(size / 2);
    }
    return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
  }
}
