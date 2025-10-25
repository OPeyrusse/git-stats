/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileChanges;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.val;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.Record;

/**
 * @author ActiveViam
 */
public class FileChangeWriter extends Writer<Map.Entry<String, FileChanges>> {

  public FileChangeWriter(Payload<Map.Entry<String, FileChanges>> data, Path outputFile) {
    super(outputFile, data);
  }

  @Override
  protected Schema createSchema() {
    return SchemaBuilder.record("FileChange")
        .namespace("com.activeviam.tooling.gitstats")
        .fields()
        .endRecord();
  }

  @Override
  protected void fillRecord(Record recordToFill, Map.Entry<String, FileChanges> entry) {
    val changes = entry.getValue();
    val commit = entry.getKey();
    recordToFill.put("commit", commit);
    recordToFill.put("path", changes.filename());
    recordToFill.put("module", computeModule(changes));
    recordToFill.put("filename", computeFileName(changes));
    recordToFill.put("additions", changes.additions());
    recordToFill.put("deletions", changes.deletions());
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

  @WithSpan("Write file changes")
  public void write() {
    writeToFile();
  }
}
