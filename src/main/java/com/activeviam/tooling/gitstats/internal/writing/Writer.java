/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import com.activeviam.tooling.gitstats.ProgramException;
import io.opentelemetry.api.trace.Span;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.io.LocalOutputFile;

/**
 * @author ActiveViam
 */
public abstract class Writer<T> {

  private final Path outputFile;
  private final Payload<T> data;

  protected Writer(Path outputFile, Payload<T> data) {
    this.outputFile = outputFile;
    this.data = data;
  }

  protected final void writeToFile() {
    Span.current().setAttribute("outputFile", this.outputFile.toString());
    try {
      Files.deleteIfExists(this.outputFile);
    } catch (final IOException e) {
      throw new ProgramException("Failed to delete the target file", e);
    }
    try (final var writer = createWriter()) {
      this.data
          .toStream()
          .forEach(
              changes -> {
                final var recordToFill = new GenericData.Record(createSchema());
                fillRecord(recordToFill, changes);
                try {
                  writer.write(recordToFill);
                } catch (IOException e) {
                  throw new ProgramException("Failed to write commit value", e);
                }
              });
    } catch (IOException e) {
      throw new ProgramException("Failed to write to parquet", e);
    }
  }

  protected abstract void fillRecord(Record recordToFill, T changes);

  private ParquetWriter<GenericRecord> createWriter() {
    try {
      final var file = new LocalOutputFile(this.outputFile);
      return AvroParquetWriter.<GenericRecord>builder(file)
          .withSchema(createSchema())
          .withPageWriteChecksumEnabled(false)
          .build();
    } catch (final IOException e) {
      throw new ProgramException("Cannot create Parquet writer", e);
    }
  }

  protected abstract Schema createSchema();
}
