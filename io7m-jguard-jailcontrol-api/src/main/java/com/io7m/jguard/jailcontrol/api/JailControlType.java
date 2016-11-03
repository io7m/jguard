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

package com.io7m.jguard.jailcontrol.api;

import com.io7m.jguard.core.JailConfigurationType;

/**
 * An API for controlling jails.
 */

public interface JailControlType
{
  /**
   * <p>Start a jail with the given configuration.</p>
   *
   * <p>The method will attempt to configure the current process to place it
   * into the jail and will then replace the process image with whatever command
   * is specified to execute within the jail. If everything succeeds, this
   * method will never return.</p>
   *
   * @param configuration The jail configuration
   *
   * @throws JailControlException If the jail fails to start for any reason
   */

  void jailStart(JailConfigurationType configuration)
    throws JailControlException;
}
