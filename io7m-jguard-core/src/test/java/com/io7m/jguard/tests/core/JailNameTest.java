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

package com.io7m.jguard.tests.core;

import com.io7m.jguard.core.JailName;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class JailNameTest
{
  @Rule public final ExpectedException expected = ExpectedException.none();

  @Test
  public void testNameValid()
  {
    JailName.of("Aa_0");
  }

  @Test
  public void testNameInvalid0()
  {
    this.expected.expect(IllegalArgumentException.class);
    JailName.of("");
  }

  @Test
  public void testNameInvalid1()
  {
    this.expected.expect(IllegalArgumentException.class);
    JailName.of("-");
  }

  @Test
  public void testNameInvalid2()
  {
    this.expected.expect(IllegalArgumentException.class);
    JailName.of(".");
  }
}
