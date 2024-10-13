/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import com.activeviam.tooling.gitstats.internal.explorer.ReadCommitDetails.CommitInfo;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.Record;

/**
 * @author ActiveViam
 */
public class CommitWriter extends Writer<CommitInfo> {

  public CommitWriter(Path outputFile, Payload<CommitInfo> data) {
    super(outputFile, data);
  }

  @Override
  protected Schema createSchema() {
    return SchemaBuilder.record("Commit")
        .namespace("com.activeviam.tooling.gitstats")
        .fields()
        .requiredString("commit")
        .requiredLong("timestamp")
        .endRecord();
  }

  @Override
  protected void fillRecord(Record record, CommitInfo commit) {
    record.put("commit", commit.sha1());
    record.put("timestamp", commit.date().getEpochSecond());
  }

  @WithSpan("Write commits")
  public void write() {
    writeToFile();
  }
}
