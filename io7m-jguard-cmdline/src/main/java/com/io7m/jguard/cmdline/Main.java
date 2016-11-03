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

package com.io7m.jguard.cmdline;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.io7m.jfunctional.Unit;
import com.io7m.jguard.core.JailConfiguration;
import com.io7m.jguard.core.JailConfigurationError;
import com.io7m.jguard.core.JailConfigurations;
import com.io7m.jguard.jailcontrol.api.JailControlException;
import com.io7m.jguard.jailcontrol.api.JailControlType;
import com.io7m.jguard.jailcontrol.fbsd_native.JailControlFBSDNative;
import com.io7m.jnull.NullCheck;
import javaslang.collection.List;
import javaslang.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import static com.io7m.jfunctional.Unit.unit;

/**
 * Main command line entry point.
 */

public final class Main implements Runnable
{
  private static final Logger LOG;

  static {
    LOG = LoggerFactory.getLogger(Main.class);
  }

  private final Map<String, CommandType> commands;
  private final JCommander commander;
  private final String[] args;
  private int exit_code;

  private Main(final String[] in_args)
  {
    this.args = NullCheck.notNull(in_args);

    final CommandRoot r = new CommandRoot();
    final CommandStart start = new CommandStart();

    this.commands = new HashMap<>(8);
    this.commands.put("start", start);

    this.commander = new JCommander(r);
    this.commander.setProgramName("jguard");
    this.commander.addCommand("start", start);
  }

  /**
   * The main entry point.
   *
   * @param args Command line arguments
   */

  public static void main(final String[] args)
  {
    final Main cm = new Main(args);
    cm.run();
    System.exit(cm.exitCode());
  }

  /**
   * @return The program exit code
   */

  public int exitCode()
  {
    return this.exit_code;
  }

  @Override
  public void run()
  {
    try {
      this.commander.parse(this.args);

      final String cmd = this.commander.getParsedCommand();
      if (cmd == null) {
        final StringBuilder sb = new StringBuilder(128);
        this.commander.usage(sb);
        LOG.info("Arguments required.\n{}", sb.toString());
        return;
      }

      final CommandType command = this.commands.get(cmd);
      command.call();

    } catch (final ParameterException e) {
      final StringBuilder sb = new StringBuilder(128);
      this.commander.usage(sb);
      LOG.error("{}\n{}", e.getMessage(), sb.toString());
      this.exit_code = 1;
    } catch (final Exception e) {
      LOG.error("{}", e.getMessage(), e);
      this.exit_code = 1;
    }
  }

  private interface CommandType extends Callable<Unit>
  {

  }

  private class CommandRoot implements CommandType
  {
    @Parameter(
      names = "-verbose",
      converter = JGLogLevelConverter.class,
      description = "Set the minimum logging verbosity level")
    private JGLogLevel verbose = JGLogLevel.LOG_INFO;

    CommandRoot()
    {

    }

    @Override
    public Unit call()
      throws Exception
    {
      final ch.qos.logback.classic.Logger root =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
          Logger.ROOT_LOGGER_NAME);
      root.setLevel(this.verbose.toLevel());
      LOG.trace("start");
      return unit();
    }
  }

  @Parameters(commandDescription = "Start a file")
  private final class CommandStart extends CommandRoot
  {
    @Parameter(
      names = "-file",
      description = "The file configuration file")
    private String file;

    @Override
    public Unit call()
      throws Exception
    {
      super.call();

      final Properties props = new Properties();
      final Path path = Paths.get(this.file);

      LOG.debug("configuration: {}", path);
      try (final InputStream is = Files.newInputStream(path)) {
        props.load(is);
        final Validation<List<JailConfigurationError>, JailConfiguration> result =
          JailConfigurations.fromProperties(path, props);
        if (result.isValid()) {
          try {
            LOG.trace("loaded configuration");
            final JailConfiguration config = result.get();
            final JailControlType control = JailControlFBSDNative.get();
            control.jailStart(config);
          } catch (final JailControlException e) {
            LOG.error("could not start jail: {}", e.getMessage());
            Main.this.exit_code = 1;
          }
        } else {
          final List<JailConfigurationError> errors = result.getError();
          errors.forEach(error -> {
            LOG.error("{}: {}: {}", error.path(), error.key(), error.message());
          });
          Main.this.exit_code = 1;
        }
      }

      return unit();
    }

    CommandStart()
    {

    }
  }
}
