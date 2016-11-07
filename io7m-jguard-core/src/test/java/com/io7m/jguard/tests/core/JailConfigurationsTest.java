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

import com.io7m.jguard.core.JailConfiguration;
import com.io7m.jguard.core.JailConfigurationError;
import com.io7m.jguard.core.JailConfigurations;
import javaslang.collection.List;
import javaslang.control.Validation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class JailConfigurationsTest
{
  @Rule public final ExpectedException expected = ExpectedException.none();

  @Test
  public void testEmpty()
  {
    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        new Properties());

    assertTrue(v.isInvalid());

    final List<JailConfigurationError> e = v.getError();
    assertEquals(5L, (long) e.size());
    assertEquals(1L, (long) e.filter(x -> "name".equals(x.key())).size());
    assertEquals(1L, (long) e.filter(x -> "path".equals(x.key())).size());
    assertEquals(1L, (long) e.filter(x -> "hostname".equals(x.key())).size());
    assertEquals(1L, (long) e.filter(x -> "start_command".equals(x.key())).size());
    assertEquals(1L, (long) e.filter(x -> "ipv4|ipv6".equals(x.key())).size());

    e.forEach(x -> System.out.printf(
      "%s: %s - %s\n",
      x.path(),
      x.key(),
      x.message()));
  }

  @Test
  public void testOK()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv4", "127.0.0.2 127.0.0.3");
    props.setProperty("ipv6", "2001:db8::ff00:42:8329 2001:db8::ff00:42:832A");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertFalse(v.isInvalid());

    final JailConfiguration c = v.get();
    assertEquals("jail0", c.name().value());
    assertEquals(Paths.get("/tmp"), c.path());
    assertEquals("jail0.example.com", c.hostname());
    final List<Inet4Address> i4 = c.ipv4Addresses();
    assertEquals(Inet4Address.getByName("127.0.0.2"), i4.get(0));
    assertEquals(Inet4Address.getByName("127.0.0.3"), i4.get(1));
    final List<Inet6Address> i6 = c.ipv6Addresses();
    assertEquals(Inet6Address.getByName("2001:db8::ff00:42:8329"), i6.get(0));
    assertEquals(Inet6Address.getByName("2001:db8::ff00:42:832A"), i6.get(1));
  }

  @Test
  public void testOKNoIPv4()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv6", "2001:db8::ff00:42:8329 2001:db8::ff00:42:832A");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertFalse(v.isInvalid());

    final JailConfiguration c = v.get();
    assertEquals("jail0", c.name().value());
    assertEquals(Paths.get("/tmp"), c.path());
    assertEquals("jail0.example.com", c.hostname());
    final List<Inet4Address> i4 = c.ipv4Addresses();
    assertTrue(i4.isEmpty());
    final List<Inet6Address> i6 = c.ipv6Addresses();
    assertEquals(Inet6Address.getByName("2001:db8::ff00:42:8329"), i6.get(0));
    assertEquals(Inet6Address.getByName("2001:db8::ff00:42:832A"), i6.get(1));
  }

  @Test
  public void testOKNoIPv6()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv4", "127.0.0.2 127.0.0.3");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertFalse(v.isInvalid());

    final JailConfiguration c = v.get();
    assertEquals("jail0", c.name().value());
    assertEquals(Paths.get("/tmp"), c.path());
    assertEquals("jail0.example.com", c.hostname());
    final List<Inet4Address> i4 = c.ipv4Addresses();
    assertEquals(Inet4Address.getByName("127.0.0.2"), i4.get(0));
    assertEquals(Inet4Address.getByName("127.0.0.3"), i4.get(1));
    final List<Inet6Address> i6 = c.ipv6Addresses();
    assertTrue(i6.isEmpty());
  }

  @Test
  public void testBadIPv4()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv4", "...");
    props.setProperty("ipv6", "2001:db8::ff00:42:8329 2001:db8::ff00:42:832A");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertTrue(v.isInvalid());

    final List<JailConfigurationError> e = v.getError();
    e.forEach(x -> System.out.printf(
      "%s: %s - %s\n",
      x.path(),
      x.key(),
      x.message()));

    assertEquals(1L, (long) e.size());
    assertEquals(1L, (long) e.filter(x -> "ipv4".equals(x.key())).size());
  }

  @Test
  public void testBadIPv6()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv4", "127.0.0.2 127.0.0.3");
    props.setProperty("ipv6", "...");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertTrue(v.isInvalid());

    final List<JailConfigurationError> e = v.getError();
    e.forEach(x -> System.out.printf(
      "%s: %s - %s\n",
      x.path(),
      x.key(),
      x.message()));

    assertEquals(1L, (long) e.size());
    assertEquals(1L, (long) e.filter(x -> "ipv6".equals(x.key())).size());
  }

  @Test
  public void testBadNotIPv4()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv4", "2001:db8::ff00:42:8329");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertTrue(v.isInvalid());

    final List<JailConfigurationError> e = v.getError();
    e.forEach(x -> System.out.printf(
      "%s: %s - %s\n",
      x.path(),
      x.key(),
      x.message()));

    assertEquals(1L, (long) e.size());
    assertEquals(1L, (long) e.filter(x -> "ipv4".equals(x.key())).size());
  }

  @Test
  public void testBadNotIPv6()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv6", "127.0.0.1");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertTrue(v.isInvalid());

    final List<JailConfigurationError> e = v.getError();
    e.forEach(x -> System.out.printf(
      "%s: %s - %s\n",
      x.path(),
      x.key(),
      x.message()));

    assertEquals(1L, (long) e.size());
    assertEquals(1L, (long) e.filter(x -> "ipv6".equals(x.key())).size());
  }

  @Test
  public void testBadName()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail~0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");
    props.setProperty("ipv4", "127.0.0.1");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertTrue(v.isInvalid());

    final List<JailConfigurationError> e = v.getError();
    e.forEach(x -> System.out.printf(
      "%s: %s - %s\n",
      x.path(),
      x.key(),
      x.message()));

    assertEquals(1L, (long) e.size());
    assertEquals(1L, (long) e.filter(x -> "name".equals(x.key())).size());
  }

  @Test
  public void testBadNoAddress0()
    throws Exception
  {
    final Properties props = new Properties();
    props.setProperty("name", "jail0");
    props.setProperty("path", "/tmp");
    props.setProperty("start_command", "/bin/sh -e -x");
    props.setProperty("hostname", "jail0.example.com");

    final Validation<List<JailConfigurationError>, JailConfiguration> v =
      JailConfigurations.fromProperties(
        Paths.get("/tmp/xyz.txt"),
        props);

    assertTrue(v.isInvalid());

    final List<JailConfigurationError> e = v.getError();
    e.forEach(x -> System.out.printf(
      "%s: %s - %s\n",
      x.path(),
      x.key(),
      x.message()));

    assertEquals(1L, (long) e.size());
    assertEquals(1L, (long) e.filter(x -> "ipv4|ipv6".equals(x.key())).size());
  }

  @Test
  public void testUnreachable()
    throws Exception
  {
    final Constructor<JailConfigurations> c =
      JailConfigurations.class.getDeclaredConstructor();
    c.setAccessible(true);

    this.expected.expect(InvocationTargetException.class);
    c.newInstance();
  }
}
