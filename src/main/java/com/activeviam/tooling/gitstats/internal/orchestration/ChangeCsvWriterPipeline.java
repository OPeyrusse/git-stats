/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.orchestration;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileChanges;
import com.activeviam.tooling.gitstats.internal.orchestration.WriteDispacher.WriteChangesAction;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.val;

/**
 * @author ActiveViam
 */
public class ChangeCsvWriterPipeline extends ACsvWritePipeline<WriteChangesAction> {

  public ChangeCsvWriterPipeline(
      Queue<Action<WriteChangesAction>> queue, Path outputDirectory, String filePattern) {
    super(queue, outputDirectory, filePattern, 1 << 14);
  }

  @Override
  protected void writeHeader(PrintWriter writer) {
    writer.println("commit,module,filename,path,additions,deletions");
  }

  @Override
  protected int processCommand(WriteChangesAction command, PrintWriter writer) {
    int lines = 0;
    for (val details : command.commits()) {
      for (val change : details.fileChanges()) {
        writer.printf(
            "%s,%s,%s,%s,%d,%d%n",
            details.commit().sha1(),
            change.filename(),
            computeModule(change),
            computeFileName(change),
            change.additions(),
            change.deletions());
        lines++;
      }
    }
    return lines;
  }

  private static final Pattern SOURCE_PATTERN = Pattern.compile("^(.*)/src/(main|test|generated)/");

  private static String computeModule(FileChanges changes) {
    val matcher = SOURCE_PATTERN.matcher(changes.filename());
    if (matcher.find()) {
      return matcher.group(1);
    }
    if (changes.filename().endsWith("pom.xml")) {
      final var filePath = Path.of(changes.filename());
      return Optional.ofNullable(filePath.getParent()).map(Path::toString).orElse("<root>");
    }
    return "n/a";
  }

  private static String computeFileName(FileChanges changes) {
    return Path.of(changes.filename()).getFileName().toString();
  }
}
