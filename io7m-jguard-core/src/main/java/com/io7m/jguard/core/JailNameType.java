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

package com.io7m.jguard.core;

import org.immutables.value.Value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The type of jail names.
 */

@ImmutableStyleType
@Value.Immutable
public interface JailNameType
{
  /**
   * A regular expression
   */

  Pattern NAME_FORMAT = Pattern.compile(
    "[\\p{IsAlphabetic}\\p{IsDigit}_]+",
    Pattern.CASE_INSENSITIVE
      | Pattern.UNICODE_CHARACTER_CLASS
      | Pattern.UNICODE_CASE);

  /**
   * @return The actual name value
   */

  @Value.Parameter(order = 0)
  String value();

  /**
   * Check type invariants.
   */

  @Value.Check
  default void check()
  {
    final Matcher matcher = NAME_FORMAT.matcher(this.value());
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
        String.format(
          "Jail name is not valid: '%s' must match %s",
          this.value(),
          NAME_FORMAT.pattern()));
    }
  }
}
