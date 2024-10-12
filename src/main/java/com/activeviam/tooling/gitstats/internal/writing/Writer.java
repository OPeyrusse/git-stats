/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import io.opentelemetry.api.trace.Span;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.util.HadoopOutputFile;

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
    try (final var writer = createWriter()) {
      this.data
          .toStream()
          .forEach(
              changes -> {
                final var record = new GenericData.Record(createSchema());
                fillRecord(record, changes);
                try {
                  writer.write(record);
                } catch (IOException e) {
                  throw new RuntimeException("Failed to write commit value", e);
                }
              });
    } catch (IOException e) {
      throw new RuntimeException("Failed to write to parquet", e);
    }
  }

  protected abstract void fillRecord(Record record, T changes);

  private ParquetWriter<GenericRecord> createWriter() {
    final HadoopOutputFile hadoopOutputFile;
    try {
      hadoopOutputFile =
          HadoopOutputFile.fromPath(
              new org.apache.hadoop.fs.Path(this.outputFile.toString()), new Configuration());
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot create hadoop path", e);
    }
    try {
      return AvroParquetWriter.<GenericRecord>builder(hadoopOutputFile)
          .withSchema(createSchema())
          .build();
    } catch (final IOException e) {
      throw new RuntimeException("Cannot create Parquet writer", e);
    }
  }

  protected abstract Schema createSchema();
}
