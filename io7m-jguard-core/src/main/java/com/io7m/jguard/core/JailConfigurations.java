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

import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.collection.List;
import javaslang.control.Validation;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Pattern;

import static javaslang.control.Validation.invalid;
import static javaslang.control.Validation.valid;

/**
 * Functions for parsing jail configurations.
 */

public final class JailConfigurations
{
  private static final Pattern SPACE = Pattern.compile("\\s+");

  private JailConfigurations()
  {
    throw new UnreachableCodeException();
  }

  private static <E, T> Validation<List<E>, T> flatten(
    final Validation<List<List<E>>, T> v)
  {
    return v.leftMap(xs -> xs.foldRight(List.empty(), List::appendAll));
  }

  private static <E, T> Validation<List<E>, T> unflatten(
    final Validation<E, T> v)
  {
    return v.leftMap(List::of);
  }

  /**
   * Parse a jail configuration from the given properties.
   *
   * @param path  The path to the properties file
   * @param props The properties
   *
   * @return A jail configuration or a list of errors
   */

  public static Validation<List<JailConfigurationError>, JailConfiguration>
  fromProperties(
    final Path path,
    final Properties props)
  {
    NullCheck.notNull(path, "Path");
    NullCheck.notNull(props, "Properties");

    final Validation<List<JailConfigurationError>, List<Inet4Address>> v_ipv4 =
      fromPropertiesIPV4Addresses(path, props);
    final Validation<List<JailConfigurationError>, List<Inet6Address>> v_ipv6 =
      fromPropertiesIPV6Addresses(path, props);
    final Validation<List<JailConfigurationError>, Tuple2<List<Inet4Address>, List<Inet6Address>>> v_addresses =
      v_ipv4.flatMap(
        v4 -> v_ipv6.flatMap(v6 -> unflatten(fromAddressLists(path, v4, v6))));

    return flatten(Validation.combine(
      unflatten(fromPropertiesName(path, props)),
      unflatten(fromPropertiesPath(path, props)),
      unflatten(fromPropertiesHostName(path, props)),
      unflatten(fromPropertiesStartCommand(path, props)),
      v_addresses).ap((jail_name, jail_path, jail_hostname, jail_start, jail_addresses) -> {
      final JailConfiguration.Builder b = JailConfiguration.builder();
      b.setName(jail_name);
      b.setHostname(jail_hostname);
      b.setPath(jail_path);
      b.setIpv4Addresses(jail_addresses._1);
      b.setIpv6Addresses(jail_addresses._2);
      b.setStartCommand(jail_start);
      return b.build();
    }));
  }

  private static Validation<JailConfigurationError, Tuple2<List<Inet4Address>, List<Inet6Address>>>
  fromAddressLists(
    final Path path,
    final List<Inet4Address> ipv4s,
    final List<Inet6Address> ipv6s)
  {
    if (ipv4s.isEmpty() && ipv6s.isEmpty()) {
      return invalid(
        JailConfigurationError.of(
          "ipv4|ipv6",
          path,
          "Jails must have at least one IPv4 or IPv6 address"));
    }
    return valid(Tuple.of(ipv4s, ipv6s));
  }

  private static Validation<JailConfigurationError, JailName>
  fromPropertiesName(
    final Path path,
    final Properties props)
  {
    if (props.containsKey("name")) {
      final String text = props.getProperty("name").trim();
      try {
        return valid(JailName.of(text));
      } catch (final IllegalArgumentException e) {
        return invalid(JailConfigurationError.of("name", path, e.getMessage()));
      }
    }
    return invalid(
      JailConfigurationError.of("name", path, "A jail name must be provided"));
  }

  private static Validation<JailConfigurationError, List<String>>
  fromPropertiesStartCommand(
    final Path path,
    final Properties props)
  {
    if (props.containsKey("start_command")) {
      final String text = props.getProperty("start_command").trim();
      return valid(List.of(SPACE.split(text)).map(String::trim));
    }
    return invalid(
      JailConfigurationError.of(
        "start_command", path, "A jail start command must be provided"));
  }

  private static Validation<JailConfigurationError, String>
  fromPropertiesHostName(
    final Path path,
    final Properties props)
  {
    if (props.containsKey("hostname")) {
      return valid(props.getProperty("hostname").trim());
    }
    return invalid(
      JailConfigurationError.of(
        "hostname", path, "A jail hostname must be provided"));
  }

  private static Validation<JailConfigurationError, Path>
  fromPropertiesPath(
    final Path path,
    final Properties props)
  {
    if (props.containsKey("path")) {
      return valid(Paths.get(props.getProperty("path").trim()));
    }
    return invalid(
      JailConfigurationError.of("path", path, "A jail path must be provided"));
  }

  private static Validation<List<JailConfigurationError>, List<Inet4Address>>
  fromPropertiesIPV4Addresses(
    final Path path,
    final Properties props)
  {
    if (props.containsKey("ipv4")) {
      final String value =
        props.getProperty("ipv4").trim();
      final List<Validation<JailConfigurationError, Inet4Address>> address_results =
        List.of(SPACE.split(value)).map(text -> tryParseIPV4(path, text));

      final List<Validation<JailConfigurationError, Inet4Address>> invalids =
        address_results.filter(Validation::isInvalid);
      if (invalids.isEmpty()) {
        return valid(address_results.map(Validation::get));
      }
      return invalid(
        address_results
          .filter(Validation::isInvalid)
          .map(Validation::getError));
    }

    return valid(List.empty());
  }

  private static Validation<List<JailConfigurationError>, List<Inet6Address>>
  fromPropertiesIPV6Addresses(
    final Path path,
    final Properties props)
  {
    if (props.containsKey("ipv6")) {
      final String value =
        props.getProperty("ipv6").trim();
      final List<Validation<JailConfigurationError, Inet6Address>> address_results =
        List.of(SPACE.split(value)).map(text -> tryParseIPV6(path, text));

      final List<Validation<JailConfigurationError, Inet6Address>> invalids =
        address_results.filter(Validation::isInvalid);
      if (invalids.isEmpty()) {
        return valid(address_results.map(Validation::get));
      }
      return invalid(
        address_results
          .filter(Validation::isInvalid)
          .map(Validation::getError));
    }

    return valid(List.empty());
  }

  private static Validation<JailConfigurationError, Inet4Address> tryParseIPV4(
    final Path path,
    final String text)
  {
    try {
      final InetAddress inet_address = Inet4Address.getByName(text.trim());
      if (inet_address instanceof Inet4Address) {
        return valid((Inet4Address) inet_address);
      }
      return invalid(invalidIPV4(path, text, "Not an IPV4 address"));
    } catch (final UnknownHostException e) {
      return invalid(invalidIPV4(path, text, e.getMessage()));
    }
  }

  private static Validation<JailConfigurationError, Inet6Address> tryParseIPV6(
    final Path path,
    final String text)
  {
    try {
      final InetAddress inet_address = Inet6Address.getByName(text.trim());
      if (inet_address instanceof Inet6Address) {
        return valid((Inet6Address) inet_address);
      }
      return invalid(invalidIPV6(path, text, "Not an IPV6 address"));
    } catch (final UnknownHostException e) {
      return invalid(invalidIPV6(path, text, e.getMessage()));
    }
  }

  private static JailConfigurationError invalidIPV4(
    final Path path,
    final String text,
    final String message)
  {
    return JailConfigurationError.of(
      "ipv4",
      path,
      String.format("Jail address is invalid: %s - %s", text, message));
  }

  private static JailConfigurationError invalidIPV6(
    final Path path,
    final String text,
    final String message)
  {
    return JailConfigurationError.of(
      "ipv6",
      path,
      String.format("Jail address is invalid: %s - %s", text, message));
  }
}
