/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats;

import java.nio.file.Path;
import picocli.CommandLine.Option;

/**
 * @author ActiveViam
 */
public class SharedOptions {

  @Option(
      names = {"-p", "--project"},
      required = true,
      description = "Project to scan")
  Path projectDirectory;

  @Option(
      names = {"-o", "--output"},
      required = true,
      description = "Output directory")
  Path outputDirectory;

  @Option(
      names = {"-b", "--branch"},
      required = true,
      description = "Branch to inspect")
  String branch;

  @Option(
      names = {"-s", "--start"},
      description = "Start commit")
  String startCommit;

  @Option(
      names = {"-n", "--count"},
      defaultValue = "10",
      description = "Number of commits to collect")
  int count;

  String resolvedStartCommit() {
    return startCommit != null ? startCommit : branch;
  }
}
