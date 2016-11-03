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

import jnr.ffi.Runtime;
import jnr.ffi.Struct;

/**
 * A libjail parameter.
 */

// CHECKSTYLE:OFF

public final class LibJailParam extends Struct
{
  public final UTF8StringRef name = new UTF8StringRef();
  public final Pointer value = new Pointer();
  public final size_t valueLength = new size_t();
  public final size_t elementLength = new size_t();
  public final Signed32 controlType = new Signed32();
  public final Signed32 structType = new Signed32();
  public final Unsigned32 flags = new Unsigned32();

  public LibJailParam(final Runtime runtime)
  {
    super(runtime);
  }

  public LibJailParam(
    final Runtime runtime,
    final Alignment alignment)
  {
    super(runtime, alignment);
  }

  public LibJailParam(
    final Runtime runtime,
    final Struct enclosing)
  {
    super(runtime, enclosing);
  }
}
