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

package com.io7m.jguard.libjail;

import jnr.ffi.annotations.Out;

/**
 * Low-level libjail interface.
 *
 * See the FreeBSD documentation for {@code jail(2)} and {@code jail(3)}.
 */

public interface LibJailType
{
  /**
   * Create jail if it doesn't exist
   */

  int JAIL_CREATE = 0x01;

  /**
   * Update parameters of existing jail
   */

  int JAIL_UPDATE = 0x02;

  /**
   * Attach to jail upon creation
   */

  int JAIL_ATTACH = 0x04;

  /**
   * Allow getting a dying jail
   */

  int JAIL_DYING = 0x08;

  /**
   * The {@code jailparam_init()} function clears a parameter record and copies
   * the name to it.  After use, it should be freed with {@code
   * jailparam_free()}.
   *
   * @param p    The parameter
   * @param name The parameter name
   *
   * @return {@code -1 on errors}
   */

  int jailparam_init(
    @Out LibJailParam p,
    String name);

  /**
   * The {@code jailparam_import()} function adds a value to a parameter record,
   * con- verting it from a string to its native form.
   *
   * @param p    The parameter
   * @param name The parameter name
   *
   * @return {@code -1 on errors}
   */

  int jailparam_import(
    LibJailParam p,
    String name);

  /**
   * The {@code jailparam_set()} function passes a list of parameters to
   * jail_set(2). The parameters are assumed to have been created with
   * jailparam_init() and {@code jailparam_import()}.
   *
   * @param p     The parameters
   * @param count The parameter count
   * @param flags The parameter flags
   *
   * @return {@code -1 on errors}
   */

  int jailparam_set(
    LibJailParam[] p,
    int count,
    int flags);

  /**
   * The {@code jailparam_free()} function frees the stored names and values in
   * a parameter list.
   *
   * @param p     The parameters
   * @param count The parameter count
   */

  void jailparam_free(
    LibJailParam[] p,
    int count);
}
