/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.shell;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileChanges;
import java.util.stream.Stream;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestChangeReader {

  @Test
  void testSimpleLine() {
    val change = ChangeReader.parseLine("1 2 file.txt");
    Assertions.assertThat(change).isEqualTo(new FileChanges("file.txt", 1, 2));
  }

  @Test
  void testLineWithComplexFile() {
    val change = ChangeReader.parseLine("1 2 composer/test/pom.xml");
    Assertions.assertThat(change).isEqualTo(new FileChanges("composer/test/pom.xml", 1, 2));
  }

  @Test
  void testFileWithoutAdditions() {
    val change = ChangeReader.parseLine("0 3 file.txt");
    Assertions.assertThat(change).isEqualTo(new FileChanges("file.txt", 0, 3));
  }

  @Test
  void testFileWithoutDeletions() {
    val change = ChangeReader.parseLine("1 0 file.txt");
    Assertions.assertThat(change).isEqualTo(new FileChanges("file.txt", 1, 0));
  }

  @Test
  void testBinaryFile() {
    val change = ChangeReader.parseLine("- - image.png");
    Assertions.assertThat(change).isEqualTo(new FileChanges("image.png", -1, -1));
  }

  static Stream<Arguments> fileChanges() {
    return Stream.of(
        Arguments.of(
            "{apm/src => composer/chunks}/tracing/Pool.java", "composer/chunks/tracing/Pool.java"),
        Arguments.of(
            "path/to/{apm/src/Before.java => composer/chunks/After.java}",
            "path/to/composer/chunks/After.java"),
        Arguments.of(
            "path/to/{apm/src/Before.java => composer/chunks/After.java}",
            "path/to/composer/chunks/After.java"),
        Arguments.of(
            "path/{apm/src => composer/chunks}/around/Pool.java",
            "path/composer/chunks/around/Pool.java"));
  }

  @ParameterizedTest
  @MethodSource("fileChanges")
  void testFileWithRenaming(final String line, final String fileName) {
    val change = ChangeReader.parseLine("1 2 " + line);
    Assertions.assertThat(change).isEqualTo(new FileChanges(fileName, 1, 2));
  }
}
