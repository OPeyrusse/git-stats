/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitInfo;
import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.FileRenaming;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import java.util.Map;
import lombok.val;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.Record;

/**
 * @author ActiveViam
 */
public class FileRenamingWriter extends Writer<Map.Entry<CommitInfo, FileRenaming>> {

  public FileRenamingWriter(Payload<Map.Entry<CommitInfo, FileRenaming>> data, Path outputFile) {
    super(outputFile, data);
  }

  @WithSpan("Write file name changes")
  public void write() {
    writeToFile();
  }

  @Override
  protected Schema createSchema() {
    return SchemaBuilder.record("FileRenaming")
        .namespace("com.activeviam.tooling.gitstats")
        .fields()
        .requiredString("commit")
        .requiredLong("timestamp")
        .requiredString("before")
        .requiredString("after")
        .endRecord();
  }

  @Override
  protected void fillRecord(Record recordToFill, Map.Entry<CommitInfo, FileRenaming> entry) {
    val commit = entry.getKey();
    val changes = entry.getValue();
    recordToFill.put("commit", commit.sha1());
    recordToFill.put("timestamp", commit.date().getEpochSecond());
    recordToFill.put("before", changes.from());
    recordToFill.put("after", changes.to());
  }
}
