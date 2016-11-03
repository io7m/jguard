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

package com.io7m.jguard.jailcontrol.fbsd_native;

import com.io7m.jguard.core.JailConfigurationType;
import com.io7m.jguard.jailcontrol.api.JailControlConfigurationException;
import com.io7m.jguard.jailcontrol.api.JailControlException;
import com.io7m.jguard.jailcontrol.api.JailControlExecutionException;
import com.io7m.jguard.jailcontrol.api.JailControlParameterException;
import com.io7m.jguard.jailcontrol.api.JailControlType;
import com.io7m.jguard.jailcontrol.api.JailControlUnavailableException;
import com.io7m.jguard.libjail.LibJailParam;
import com.io7m.jguard.libjail.LibJailType;
import com.io7m.jnull.NullCheck;
import javaslang.collection.List;
import jnr.ffi.LibraryLoader;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

/**
 * A FreeBSD-native implementation of the {@link JailControlType} API.
 */

public final class JailControlFBSDNative implements JailControlType
{
  private static final Logger LOG;

  static {
    LOG = LoggerFactory.getLogger(JailControlFBSDNative.class);
  }

  private final Runtime runtime;
  private final POSIX posix;
  private final StrErrorType strerror;
  private final LibJailType libjail;

  private JailControlFBSDNative(
    final Runtime in_runtime,
    final POSIX in_posix,
    final StrErrorType in_strerror,
    final LibJailType in_libjail)
  {
    this.runtime = NullCheck.notNull(in_runtime, "Runtime");
    this.posix = NullCheck.notNull(in_posix, "POSIX");
    this.strerror = NullCheck.notNull(in_strerror, "Strerror");
    this.libjail = NullCheck.notNull(in_libjail, "Libjail");
  }

  /**
   * Interface to the standard library's {@code strerror} function.
   */

  public interface StrErrorType
  {
    /**
     * @param e The errno value
     *
     * @return An error message for the given errno value
     */

    String strerror(int e);
  }

  /**
   * @return An implementation of the jail control API
   *
   * @throws JailControlUnavailableException If the current platform has no jail
   *                                         API implementation
   */

  public static JailControlType get()
    throws JailControlUnavailableException
  {
    try {
      final Runtime runtime = Runtime.getSystemRuntime();
      final POSIX posix = POSIXFactory.getNativePOSIX();

      LOG.debug("creating libc loader");
      final LibraryLoader<StrErrorType> c_loader =
        LibraryLoader.create(StrErrorType.class);
      c_loader.failImmediately();

      LOG.debug("loading libc library");
      final StrErrorType strerror = c_loader.load("c");
      LOG.debug("loaded libc library: {}", strerror);

      LOG.debug("creating jail loader");
      final LibraryLoader<LibJailType> jail_loader =
        LibraryLoader.create(LibJailType.class);
      jail_loader.failImmediately();

      LOG.debug("loading jail library");
      final LibJailType libjail = jail_loader.load("jail");
      LOG.debug("loaded jail library: {}", libjail);

      return new JailControlFBSDNative(runtime, posix, strerror, libjail);
    } catch (final UnsatisfiedLinkError e) {
      throw new JailControlUnavailableException(e);
    }
  }

  @Override
  public void jailStart(
    final JailConfigurationType configuration)
    throws JailControlException
  {
    final LibJailParam[] params =
      Struct.arrayOf(this.runtime, LibJailParam.class, 5);

    int index = 0;
    int r = 0;

    try {
      r = this.libjail.jailparam_init(params[index], "path");
      this.checkParameterInit("jailparam_init", "path", r, this.posix.errno());
      final String path_text = configuration.path().toString();
      r = this.libjail.jailparam_import(params[index], path_text);
      this.checkParameterImport(
        "jailparam_import", "path", path_text, r, this.posix.errno());

      ++index;
      r = this.libjail.jailparam_init(params[index], "name");
      this.checkParameterInit("jailparam_init", "name", r, this.posix.errno());
      final String name_text = configuration.name().toString();
      r = this.libjail.jailparam_import(params[index], name_text);
      this.checkParameterImport(
        "jailparam_import", "name", name_text, r, this.posix.errno());

      ++index;
      r = this.libjail.jailparam_init(params[index], "host.hostname");
      this.checkParameterInit(
        "jailparam_init",
        "host.hostname",
        r,
        this.posix.errno());
      final String hostname_text = configuration.hostname();
      r = this.libjail.jailparam_import(params[index], hostname_text);
      this.checkParameterImport(
        "jailparam_import",
        "host.hostname",
        hostname_text,
        r,
        this.posix.errno());

      {
        final List<Inet4Address> ipv4 = configuration.ipv4Addresses();
        if (!ipv4.isEmpty()) {
          final String text =
            ipv4.toJavaStream()
              .map(InetAddress::getHostAddress)
              .collect(joining(" "));

          ++index;
          r = this.libjail.jailparam_init(params[index], "ip4.addr");
          this.checkParameterInit(
            "jailparam_init",
            "ip4.addr",
            r,
            this.posix.errno());
          r = this.libjail.jailparam_import(params[index], text);
          this.checkParameterImport(
            "jailparam_import",
            "ip4.addr",
            text,
            r,
            this.posix.errno());
        }
      }

      {
        final List<Inet6Address> ipv6 = configuration.ipv6Addresses();
        if (!ipv6.isEmpty()) {
          final String text =
            ipv6.toJavaStream()
              .map(Inet6Address::getHostAddress)
              .collect(joining(" "));

          ++index;
          r = this.libjail.jailparam_init(params[index], "ip6.addr");
          this.checkParameterInit(
            "jailparam_init",
            "ip6.addr",
            r,
            this.posix.errno());
          r = this.libjail.jailparam_import(params[index], text);
          this.checkParameterImport(
            "jailparam_import",
            "ip6.addr",
            text,
            r,
            this.posix.errno());
        }
      }

      int flags = 0;
      flags |= LibJailType.JAIL_ATTACH;
      flags |= LibJailType.JAIL_CREATE;
      flags |= LibJailType.JAIL_CREATE;
      r = this.libjail.jailparam_set(params, index + 1, flags);
      this.checkConfig("jailparam_set", r, this.posix.errno());

      {
        final List<String> cmd = configuration.startCommand();
        final String[] args = new String[cmd.length() + 1];

        LOG.trace("exec:");
        for (int cmd_index = 0; cmd_index < cmd.length(); ++cmd_index) {
          final String arg = cmd.get(cmd_index);
          args[cmd_index] = arg;
          LOG.trace("  [{}] {}", Integer.valueOf(cmd_index), arg);
        }

        r = this.posix.execve(args[0], args, new String[]{null});

        {
          final StringBuilder sb = new StringBuilder(128);
          sb.append("Failed to execute start command.");
          sb.append(System.lineSeparator());
          sb.append("  Function:   ");
          sb.append("execve");
          sb.append(System.lineSeparator());
          sb.append("  Command:    ");
          sb.append(cmd.toJavaStream().collect(Collectors.joining(" ")));
          sb.append(System.lineSeparator());
          sb.append("  Error code: ");
          sb.append(r);
          sb.append(System.lineSeparator());
          sb.append("  Message:    ");
          sb.append(this.strerror.strerror(this.posix.errno()));
          sb.append(System.lineSeparator());
          throw new JailControlExecutionException(sb.toString());
        }
      }
    } finally {
      this.libjail.jailparam_free(params, index + 1);
    }
  }

  private void checkParameterInit(
    final String function,
    final String name,
    final int code,
    final int errno)
    throws JailControlParameterException
  {
    if (code == -1) {
      final StringBuilder sb = new StringBuilder(128);
      sb.append("Failed to prepare a jail parameter.");
      sb.append(System.lineSeparator());
      sb.append("  Function:   ");
      sb.append(function);
      sb.append(System.lineSeparator());
      sb.append("  Name:       ");
      sb.append(name);
      sb.append(System.lineSeparator());
      sb.append("  Error code: ");
      sb.append(code);
      sb.append(System.lineSeparator());
      sb.append("  Message:    ");
      sb.append(this.strerror.strerror(errno));
      sb.append(System.lineSeparator());
      throw new JailControlParameterException(sb.toString());
    }
  }

  private void checkParameterImport(
    final String function,
    final String name,
    final String value,
    final int code,
    final int errno)
    throws JailControlParameterException
  {
    if (code == -1) {
      final StringBuilder sb = new StringBuilder(128);
      sb.append("Failed to import a jail parameter.");
      sb.append(System.lineSeparator());
      sb.append("  Function:   ");
      sb.append(function);
      sb.append(System.lineSeparator());
      sb.append("  Name:       ");
      sb.append(name);
      sb.append(System.lineSeparator());
      sb.append("  Value:      ");
      sb.append(value);
      sb.append(System.lineSeparator());
      sb.append("  Error code: ");
      sb.append(code);
      sb.append(System.lineSeparator());
      sb.append("  Message:    ");
      sb.append(this.strerror.strerror(errno));
      sb.append(System.lineSeparator());
      throw new JailControlParameterException(sb.toString());
    }
  }

  private void checkConfig(
    final String function,
    final int code,
    final int errno)
    throws JailControlConfigurationException
  {
    if (code == -1) {
      final StringBuilder sb = new StringBuilder(128);
      sb.append("Failed to configure the jail.");
      sb.append(System.lineSeparator());
      sb.append("  Function:   ");
      sb.append(function);
      sb.append(System.lineSeparator());
      sb.append("  Error code: ");
      sb.append(code);
      sb.append(System.lineSeparator());
      sb.append("  Message:    ");
      sb.append(this.strerror.strerror(errno));
      sb.append(System.lineSeparator());
      throw new JailControlConfigurationException(sb.toString());
    }
  }
}
