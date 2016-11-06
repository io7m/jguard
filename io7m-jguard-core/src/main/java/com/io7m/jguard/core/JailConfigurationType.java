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

import javaslang.collection.List;
import org.immutables.value.Value;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * The type of jail configurations.
 */

@Value.Immutable
@ImmutableStyleType
public interface JailConfigurationType
{
  /**
   * @return The path to the root directory of the jail
   */

  @Value.Parameter(order = 0)
  Path path();

  /**
   * @return The name of the jail
   */

  @Value.Parameter(order = 1)
  JailName name();

  /**
   * @return The list of IPv4 addresses for the jail
   */

  @Value.Parameter(order = 2)
  List<Inet4Address> ipv4Addresses();

  /**
   * @return The list of IPv6 addresses for the jail
   */

  @Value.Parameter(order = 3)
  List<Inet6Address> ipv6Addresses();

  /**
   * @return The hostname for the jail
   */

  @Value.Parameter(order = 4)
  String hostname();

  /**
   * @return The command that will be executed when starting the jail
   */

  @Value.Parameter(order = 5)
  List<String> startCommand();

  /**
   * Check preconditions for the type.
   */

  @Value.Check
  default void checkPreconditions()
  {
    if (this.ipv4Addresses().isEmpty() && this.ipv6Addresses().isEmpty()) {
      throw new IllegalArgumentException(
        "Must specify at least one IPv4 address or at least one IPv6 address");
    }
  }

  /**
   * Serialize the current configuration to Java properties.
   *
   * @return The serialized configuration
   */

  default Properties toProperties()
  {
    final Properties p = new Properties();
    p.setProperty("path", this.path().toString());
    p.setProperty("name", this.name().value());
    p.setProperty("hostname", this.hostname());

    p.setProperty(
      "start_command",
      this.startCommand()
        .toJavaStream()
        .collect(Collectors.joining(" ")));

    if (!this.ipv4Addresses().isEmpty()) {
      p.setProperty(
        "ipv4",
        this.ipv4Addresses()
          .toJavaStream()
          .map(Inet4Address::getHostAddress)
          .collect(Collectors.joining(" ")));
    }

    if (!this.ipv6Addresses().isEmpty()) {
      p.setProperty(
        "ipv6",
        this.ipv6Addresses()
          .toJavaStream()
          .map(Inet6Address::getHostAddress)
          .collect(Collectors.joining(" ")));
    }

    return p;
  }
}
