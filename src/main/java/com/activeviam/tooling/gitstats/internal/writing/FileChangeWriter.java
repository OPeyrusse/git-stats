/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import com.activeviam.tooling.gitstats.internal.ReadCommitDetails.FileChanges;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.Record;

/**
 * @author ActiveViam
 */
public class FileChangeWriter extends Writer<FileChanges> {

  private final String commit;

  public FileChangeWriter(String commit, Payload<FileChanges> data, Path outputFile) {
    super(outputFile, data);
    this.commit = commit;
  }

  @Override
  protected Schema createSchema() {
    return SchemaBuilder.record("Commit")
        .namespace("com.activeviam.tooling.gitstats")
        .fields()
        .requiredString("commit")
        .requiredString("module")
        .requiredString("filename")
        .requiredString("path")
        .requiredLong("additions")
        .requiredLong("deletions")
        .endRecord();
  }

  @Override
  protected void fillRecord(Record record, FileChanges changes) {
    record.put("commit", commit);
    record.put("path", changes.filename());
    record.put("module", computeModule(changes));
    record.put("filename", computeFileName(changes));
    record.put("additions", changes.additions());
    record.put("deletions", changes.deletions());
  }

  private static String computeModule(FileChanges changes) {
    final var mainSrcIndex = changes.filename().indexOf("/src/main");
    if (mainSrcIndex >= 0) {
      return changes.filename().substring(mainSrcIndex);
    }
    final var testSrcIndex = changes.filename().indexOf("/src/test");
    if (testSrcIndex >= 0) {
      return changes.filename().substring(testSrcIndex);
    }
    if (changes.filename().endsWith("pom.xml")) {
      final var filePath = Path.of(changes.filename());
      return filePath.getParent().toString();
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
