/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.shell;

import com.activeviam.tooling.gitstats.Application.IndentSpec;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestIndentationReader {

  @Nested
  class IndentSpecParsing {

    @Test
    void parseTwoTabs() {
      val spec = IndentSpec.parse("2t");
      Assertions.assertThat(spec.size()).isEqualTo(2);
      Assertions.assertThat(spec.type()).isEqualTo('t');
      Assertions.assertThat(spec.indentUnit()).isEqualTo("\t\t");
    }

    @Test
    void parseFourSpaces() {
      val spec = IndentSpec.parse("4s");
      Assertions.assertThat(spec.size()).isEqualTo(4);
      Assertions.assertThat(spec.type()).isEqualTo('s');
      Assertions.assertThat(spec.indentUnit()).isEqualTo("    ");
    }

    @Test
    void parseThreeSpaces() {
      val spec = IndentSpec.parse("3s");
      Assertions.assertThat(spec.indentUnit()).isEqualTo("   ");
    }

    @Test
    void invalidFormatNull() {
      Assertions.assertThatThrownBy(() -> IndentSpec.parse(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidFormatTooShort() {
      Assertions.assertThatThrownBy(() -> IndentSpec.parse("t"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidFormatBadType() {
      Assertions.assertThatThrownBy(() -> IndentSpec.parse("2x"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidFormatZeroSize() {
      Assertions.assertThatThrownBy(() -> IndentSpec.parse("0s"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidFormatNegativeSize() {
      Assertions.assertThatThrownBy(() -> IndentSpec.parse("-1s"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class CountIndentLevels {

    @Test
    void noIndent() {
      Assertions.assertThat(IndentationReader.countIndentLevels("hello", "  ")).isEqualTo(0);
    }

    @Test
    void oneLevel() {
      Assertions.assertThat(IndentationReader.countIndentLevels("  hello", "  ")).isEqualTo(1);
    }

    @Test
    void twoLevels() {
      Assertions.assertThat(IndentationReader.countIndentLevels("    hello", "  ")).isEqualTo(2);
    }

    @Test
    void partialIndentFloors() {
      // 5 spaces with 3-space indent → floor(5/3) = 1
      Assertions.assertThat(IndentationReader.countIndentLevels("     hello", "   ")).isEqualTo(1);
    }

    @Test
    void tabIndent() {
      Assertions.assertThat(IndentationReader.countIndentLevels("\t\thello", "\t")).isEqualTo(2);
    }

    @Test
    void twoTabUnit() {
      // 3 tabs with 2-tab indent → floor(3/2) = 1
      Assertions.assertThat(IndentationReader.countIndentLevels("\t\t\thello", "\t\t")).isEqualTo(1);
    }
  }

  @Nested
  class ComputeMedian {

    @Test
    void oddCount() {
      Assertions.assertThat(IndentationReader.computeMedian(List.of(1, 2, 3))).isEqualTo(2);
    }

    @Test
    void evenCount() {
      // (2+3)/2 = 2 (integer division)
      Assertions.assertThat(IndentationReader.computeMedian(List.of(1, 2, 3, 4))).isEqualTo(2);
    }

    @Test
    void singleElement() {
      Assertions.assertThat(IndentationReader.computeMedian(List.of(5))).isEqualTo(5);
    }
  }

  @Nested
  class ParseOutput {

    @Test
    void singleJavaFile() {
      val diff = """
          diff --git a/dev/null b/src/Main.java
          --- /dev/null
          +++ b/src/Main.java
          @@ -0,0 +1,5 @@
          +public class Main {
          +  void run() {
          +    System.out.println("hello");
          +  }
          +}
          """;
      val spec = IndentSpec.parse("2s");
      val results = IndentationReader.parseOutput(readerOf(diff), spec);

      Assertions.assertThat(results).hasSize(1);
      val stats = results.getFirst();
      Assertions.assertThat(stats.path()).isEqualTo("src/Main.java");
      Assertions.assertThat(stats.minIndent()).isEqualTo(0);
      Assertions.assertThat(stats.maxIndent()).isEqualTo(2);
      // Lines: 0, 1, 2, 1, 0 → mean = 4/5 = 0.8, median = 1
      Assertions.assertThat(stats.meanIndent()).isCloseTo(0.8, Assertions.within(0.01));
      Assertions.assertThat(stats.medianIndent()).isEqualTo(1);
    }

    @Test
    void blankLinesExcluded() {
      val diff = """
          diff --git a/dev/null b/src/Main.java
          --- /dev/null
          +++ b/src/Main.java
          @@ -0,0 +1,4 @@
          +public class Main {
          +
          +  void run() {}
          +}
          """;
      val spec = IndentSpec.parse("2s");
      val results = IndentationReader.parseOutput(readerOf(diff), spec);

      Assertions.assertThat(results).hasSize(1);
      // The blank line should be excluded, so 3 lines: 0, 1, 0
      val stats = results.getFirst();
      Assertions.assertThat(stats.minIndent()).isEqualTo(0);
      Assertions.assertThat(stats.maxIndent()).isEqualTo(1);
    }

    @Test
    void nonJavaFilesIgnored() {
      val diff = """
          diff --git a/dev/null b/readme.md
          --- /dev/null
          +++ b/readme.md
          @@ -0,0 +1,2 @@
          +# Title
          +Content
          """;
      val spec = IndentSpec.parse("2s");
      val results = IndentationReader.parseOutput(readerOf(diff), spec);

      Assertions.assertThat(results).isEmpty();
    }

    @Test
    void multipleFiles() {
      val diff = """
          diff --git a/dev/null b/src/A.java
          --- /dev/null
          +++ b/src/A.java
          @@ -0,0 +1,2 @@
          +class A {
          +}
          diff --git a/dev/null b/src/B.java
          --- /dev/null
          +++ b/src/B.java
          @@ -0,0 +1,2 @@
          +class B {
          +}
          """;
      val spec = IndentSpec.parse("2s");
      val results = IndentationReader.parseOutput(readerOf(diff), spec);

      Assertions.assertThat(results).hasSize(2);
      Assertions.assertThat(results.get(0).path()).isEqualTo("src/A.java");
      Assertions.assertThat(results.get(1).path()).isEqualTo("src/B.java");
    }
  }

  private static BufferedReader readerOf(String content) {
    return new BufferedReader(new StringReader(content));
  }
}
