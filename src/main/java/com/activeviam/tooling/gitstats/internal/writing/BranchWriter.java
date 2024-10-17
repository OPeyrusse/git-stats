/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.Record;

/**
 * @author ActiveViam
 */
public class BranchWriter extends Writer<String> {

  private final String branch;

  public BranchWriter(Path outputFile, String branch, Payload<String> data) {
    super(outputFile, data);
    this.branch = branch;
  }

  @Override
  protected Schema createSchema() {
    return SchemaBuilder.record("Commit")
        .namespace("com.activeviam.tooling.gitstats")
        .fields()
        .requiredString("branch")
        .requiredString("commit")
        .endRecord();
  }

  @Override
  protected void fillRecord(Record recordToFill, String changes) {
    recordToFill.put("branch", branch);
    recordToFill.put("commit", changes);
  }

  @WithSpan("Write branch details")
  public void write() {
    writeToFile();
  }
}
