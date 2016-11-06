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
import com.io7m.jguard.jailbuild.api.JailArchiveFormat;
import com.io7m.jguard.jailbuild.api.JailBuildType;
import com.io7m.jguard.jailbuild.api.JailDownloadOctetsPerSecond;
import com.io7m.jguard.jailbuild.api.JailDownloadProgressType;
import com.io7m.jguard.jailbuild.implementation.JailBuild;
import com.io7m.jguard.jailcontrol.api.JailControlException;
import com.io7m.jguard.jailcontrol.api.JailControlType;
import com.io7m.jguard.jailcontrol.fbsd_native.JailControlFBSDNative;
import com.io7m.jnull.NullCheck;
import javaslang.collection.List;
import javaslang.control.Validation;
import jnr.posix.POSIXFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    final CommandRoot r =
      new CommandRoot();
    final CommandStart start =
      new CommandStart();
    final CommandDownloadBinaryArchive download =
      new CommandDownloadBinaryArchive();
    final CommandCreateJailBase create_base =
      new CommandCreateJailBase();
    final CommandVersion version =
      new CommandVersion();

    this.commands = new HashMap<>(8);
    this.commands.put("start", start);
    this.commands.put("download-base-archive", download);
    this.commands.put("create-jail-base", create_base);
    this.commands.put("version", version);

    this.commander = new JCommander(r);
    this.commander.setProgramName("jguard");
    this.commander.addCommand("start", start);
    this.commander.addCommand("download-base-archive", download);
    this.commander.addCommand("create-jail-base", create_base);
    this.commander.addCommand("version", version);
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

  @Parameters(commandDescription = "Retrieve the program version")
  private final class CommandVersion extends CommandRoot
  {
    CommandVersion()
    {

    }

    @Override
    public Unit call()
      throws Exception
    {
      super.call();

      final Package p = this.getClass().getPackage();
      System.out.printf(
        "%s %s %s\n",
        p.getImplementationVendor(),
        p.getImplementationTitle(),
        p.getImplementationVersion());

      return unit();
    }
  }

  @Parameters(commandDescription = "Create a base jail and template from an archive")
  private final class CommandCreateJailBase extends CommandRoot
  {
    @Parameter(
      names = "-base",
      required = true,
      description = "The created base directory")
    private String base;

    @Parameter(
      names = "-base-template",
      required = true,
      description = "The created base template directory")
    private String base_template;

    @Parameter(
      names = "-archive",
      required = true,
      description = "Select a specific archive file")
    private String archive_file;

    @Parameter(
      names = "-archive-format",
      description = "Explicitly specify the archive format")
    private JailArchiveFormat archive_format;

    CommandCreateJailBase()
    {

    }

    @Override
    public Unit call()
      throws Exception
    {
      super.call();

      final Path jail_base_archive =
        Paths.get(this.archive_file).toAbsolutePath();
      final Path jail_base =
        Paths.get(this.base).toAbsolutePath();
      final Path jail_base_template =
        Paths.get(this.base_template).toAbsolutePath();

      LOG.debug("archive:        {}", jail_base_archive);
      LOG.debug("base:           {}", jail_base);
      LOG.debug("base-template:  {}", jail_base_template);

      final ExecutorService pool =
        Executors.newSingleThreadExecutor();
      final JailBuildType jb =
        JailBuild.get(JailBuild.clients(), POSIXFactory.getNativePOSIX(), pool);

      try {
        if (this.archive_format == null) {
          final Optional<JailArchiveFormat> format_opt =
            JailArchiveFormat.inferFrom(jail_base_archive);
          if (format_opt.isPresent()) {
            this.archive_format = format_opt.get();
          }
        }

        LOG.debug("archive-format: {}", this.archive_format);

        jb.jailCreateBase(
          jail_base_archive,
          this.archive_format,
          jail_base,
          jail_base_template);

        return unit();
      } finally {
        LOG.debug("stopping thread pool");
        pool.shutdown();
        pool.awaitTermination(10L, TimeUnit.SECONDS);
      }
    }
  }

  @Parameters(commandDescription = "Download a binary archive for creating a jail")
  private final class CommandDownloadBinaryArchive extends CommandRoot
  {
    @Parameter(
      names = "-archive",
      required = true,
      description = "The output file")
    private String file;

    @Parameter(
      names = "-arch",
      description = "Override the system architecture")
    private String arch;

    @Parameter(
      names = "-release",
      description = "Override the system release")
    private String release;

    @Parameter(
      names = "-fetch-archive",
      description = "Select a specific archive file")
    private String archive_file = "base.txz";

    @Parameter(
      names = "-base-uri",
      description = "Override the base URI")
    private URI base_uri = URI.create(
      "http://ftp.freebsd.org/pub/FreeBSD/releases/");

    @Parameter(
      names = "-retry",
      description = "Set the number of retries for failed downloads (0 is unlimited)")
    private int retry_max = 10;

    CommandDownloadBinaryArchive()
    {

    }

    @Override
    public Unit call()
      throws Exception
    {
      super.call();

      final String archive_arch = this.getArch();
      LOG.debug("arch: {}", archive_arch);
      final String archive_release = this.getRelease();
      LOG.debug("release: {}", archive_release);

      final ExecutorService pool = Executors.newSingleThreadExecutor();
      final JailBuildType jb =
        JailBuild.get(JailBuild.clients(), POSIXFactory.getNativePOSIX(), pool);

      final Path out_file = Paths.get(this.file);
      final Path out_file_tmp = Paths.get(this.file + ".tmp");

      try {
        int attempt = 0;

        while (true) {
          try {
            ++attempt;

            final JailDownloadProgressType progress =
              JailDownloadOctetsPerSecond.get(
                (total_expected, total_received, octets_per_second) ->
                  LOG.info(
                    "download ({}) {} / {} bytes ({} MiB/s)",
                    CommandDownloadBinaryArchive.this.archive_file,
                    Long.valueOf(total_received),
                    Long.valueOf(total_expected),
                    Double.valueOf((double) octets_per_second / 1000_000.0)),
                Clock.systemUTC());

            LOG.info(
              "downloading {} - attempt {} of {}",
              this.archive_file,
              Integer.valueOf(attempt),
              Integer.valueOf(this.retry_max));

            final CompletableFuture<Void> f = jb.jailDownloadBinaryArchive(
              out_file_tmp,
              this.base_uri,
              archive_arch,
              archive_release,
              this.archive_file,
              Optional.of(progress));
            f.get();

            LOG.info("download completed");
            LOG.debug("rename: {} → {}", out_file_tmp, out_file);
            Files.move(
              out_file_tmp,
              out_file,
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);

            return unit();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (final ExecutionException e) {
            LOG.error("download failed: ", e.getCause());

            final boolean retry =
              this.retry_max <= 0 || attempt < this.retry_max;

            if (retry) {
              LOG.debug("waiting 3 seconds for retry");
              TimeUnit.SECONDS.sleep(3L);
            } else {
              LOG.error("giving up after too many retries");
              Main.this.exit_code = 1;
              return unit();
            }
          }
        }
      } finally {
        LOG.debug("stopping thread pool");
        pool.shutdown();
        pool.awaitTermination(10L, TimeUnit.SECONDS);
      }
    }

    private String getArch()
    {
      String archive_arch = System.getProperty("os.arch");
      if (this.arch != null) {
        archive_arch = this.arch;
      }
      if (archive_arch == null) {
        throw new ParameterException(
          "Could not detect the system architecture and no override was provided");
      }
      return archive_arch;
    }

    private String getRelease()
    {
      String archive_release = System.getProperty("os.version");
      if (this.release != null) {
        archive_release = this.release;
      }
      if (archive_release == null) {
        throw new ParameterException(
          "Could not detect the system version and no override was provided");
      }
      return archive_release;
    }
  }

  @Parameters(commandDescription = "Start a jail")
  private final class CommandStart extends CommandRoot
  {
    @Parameter(
      names = "-file",
      required = true,
      description = "The jail configuration file")
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
