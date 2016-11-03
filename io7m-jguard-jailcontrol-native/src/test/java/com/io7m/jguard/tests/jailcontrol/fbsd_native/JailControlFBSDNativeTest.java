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

package com.io7m.jguard.tests.jailcontrol.fbsd_native;

import com.io7m.jguard.jailcontrol.api.JailControlUnavailableException;
import com.io7m.jguard.jailcontrol.fbsd_native.JailControlFBSDNative;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class JailControlFBSDNativeTest
{
  @Rule public ExpectedException expected = ExpectedException.none();

  @Test
  public void testGetOnFreeBSD()
    throws Exception
  {
    Assume.assumeTrue(
      "Platform is FreeBSD",
      System.getProperty("os.name").toUpperCase().contains("FREEBSD"));

    JailControlFBSDNative.get();
  }

  @Test
  public void testGetOnNotFreeBSD()
    throws Exception
  {
    Assume.assumeTrue(
      "Platform is not FreeBSD",
      !System.getProperty("os.name").toUpperCase().contains("FREEBSD"));

    this.expected.expect(JailControlUnavailableException.class);
    JailControlFBSDNative.get();
  }
}
