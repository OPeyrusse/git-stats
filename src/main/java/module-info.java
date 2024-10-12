module com.activeviam.tooling.gitstats {
  requires commons.cli;
  requires java.logging;
  requires io.opentelemetry.instrumentation_annotations;
  requires org.mapstruct.processor;
  requires io.opentelemetry.api;
  requires org.apache.avro;
  requires parquet.hadoop;
  requires parquet.avro;
  requires hadoop.common;
}
