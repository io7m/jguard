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

package com.io7m.jguard.cmdline;

import com.beust.jcommander.IStringConverter;
import com.io7m.jguard.jailbuild.api.JailArchiveFormat;

/**
 * A converter for {@link JailArchiveFormat} values.
 */

public final class JGArchiveFormatConverter implements
  IStringConverter<JailArchiveFormat>
{
  /**
   * Construct a new converter.
   */

  public JGArchiveFormatConverter()
  {

  }

  @Override
  public JailArchiveFormat convert(final String value)
  {
    for (final JailArchiveFormat v : JailArchiveFormat.values()) {
      if (value.equals(v.getName())) {
        return v;
      }
    }

    throw new JGArchiveFormatUnrecognized(
      "Unrecognized format: " + value);
  }
}
