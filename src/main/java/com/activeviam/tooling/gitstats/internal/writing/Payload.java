/*
 * (C) ActiveViam 2024
 * ALL RIGHTS RESERVED. This material is the CONFIDENTIAL and PROPRIETARY
 * property of ActiveViam. Any unauthorized use,
 * reproduction or transfer of this material is strictly prohibited
 */

package com.activeviam.tooling.gitstats.internal.writing;

import java.util.stream.Stream;

/**
 * @author ActiveViam
 */
public interface Payload<U> {

  Stream<U> toStream();
}
