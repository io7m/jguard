/*
 * Copyright © 2016 <code@io7m.com> http://io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.jguard.jailbuild.api;

import com.io7m.jnull.NullCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

public enum JailArchiveFormat
{
  JAIL_ARCHIVE_FORMAT_TAR_XZ("txz");

  private static final Logger LOG;

  static {
    LOG = LoggerFactory.getLogger(JailArchiveFormat.class);
  }

  private final String name;

  public String getName()
  {
    return this.name;
  }

  JailArchiveFormat(
    final String in_name)
  {
    this.name = NullCheck.notNull(in_name, "Name");
  }

  public static Optional<JailArchiveFormat> inferFrom(
    final Path path)
  {
    NullCheck.notNull(path, "Path");

    final String path_text = path.toString();
    if (path_text.endsWith(".txz")) {
      return Optional.of(JAIL_ARCHIVE_FORMAT_TAR_XZ);
    }

    return Optional.empty();
  }
}
