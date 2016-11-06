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

package com.io7m.jguard.jailbuild.implementation;

import com.io7m.jguard.jailbuild.api.JailArchiveFormat;
import com.io7m.jguard.jailbuild.api.JailBuildType;
import com.io7m.jguard.jailbuild.api.JailDownloadProgressType;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import javaslang.collection.List;
import jnr.ffi.LibraryLoader;
import jnr.posix.POSIX;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The default implementation of the {@link JailBuildType} API.
 */

public final class JailBuild implements JailBuildType
{
  private static final Logger LOG;

  static {
    LOG = LoggerFactory.getLogger(JailBuild.class);
  }

  private final ExecutorService pool;
  private final Supplier<CloseableHttpClient> clients;
  private final StrErrorType strerror;
  private final POSIX posix;

  /**
   * The symlinks created for jail templates.
   */

  public static final List<String> JAIL_TEMPLATE_LINKS =
    List.of(
      "bin",
      "boot",
      "lib",
      "libexec",
      "rescue",
      "sbin",
      "usr/bin",
      "usr/include",
      "usr/lib",
      "usr/libdata",
      "usr/libexec",
      "usr/sbin",
      "usr/share",
      "usr/src",
      "usr/lib32",
      "usr/games",
      "usr/ports",
      "sys");

  private JailBuild(
    final Supplier<CloseableHttpClient> in_clients,
    final POSIX in_posix,
    final StrErrorType in_strerror,
    final ExecutorService in_pool)
  {
    this.pool = NullCheck.notNull(in_pool, "Pool");
    this.clients = NullCheck.notNull(in_clients, "Clients");
    this.strerror = NullCheck.notNull(in_strerror, "Strerror");
    this.posix = NullCheck.notNull(in_posix, "POSIX");
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
   * @param in_clients An HTTP client supplier
   * @param in_posix   A POSIX interface
   * @param in_pool    An executor service pool
   *
   * @return A jail builder API
   */

  public static JailBuildType get(
    final Supplier<CloseableHttpClient> in_clients,
    final POSIX in_posix,
    final ExecutorService in_pool)
  {
    LOG.debug("creating libc loader");
    final LibraryLoader<StrErrorType> c_loader =
      LibraryLoader.create(StrErrorType.class);
    c_loader.failImmediately();

    LOG.debug("loading libc library");
    final StrErrorType strerror = c_loader.load("c");
    LOG.debug("loaded libc library: {}", strerror);

    return new JailBuild(in_clients, in_posix, strerror, in_pool);
  }

  @Override
  public CompletableFuture<Void> jailDownloadBinaryArchive(
    final Path file,
    final URI base,
    final String arch,
    final String release,
    final String archive_file,
    final Optional<JailDownloadProgressType> progress)
  {
    NullCheck.notNull(file, "File");
    NullCheck.notNull(base, "Base");
    NullCheck.notNull(arch, "Arch");
    NullCheck.notNull(release, "Release");
    NullCheck.notNull(progress, "Progress");

    final CompletableFuture<Void> future = new CompletableFuture<>();
    final BooleanSupplier is_cancelled = future::isCancelled;

    this.pool.submit(() -> {
      try {
        this.download(
          is_cancelled, file, base, arch, release, archive_file, progress);
        future.complete(null);
      } catch (final Throwable e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  @Override
  public void jailDownloadBinaryArchiveSync(
    final Path file,
    final URI base,
    final String arch,
    final String release,
    final String archive_file,
    final Optional<JailDownloadProgressType> progress)
    throws IOException
  {
    NullCheck.notNull(file, "File");
    NullCheck.notNull(base, "Base");
    NullCheck.notNull(arch, "Arch");
    NullCheck.notNull(release, "Release");
    NullCheck.notNull(progress, "Progress");

    this.download(
      () -> false, file, base, arch, release, archive_file, progress);
  }

  @Override
  public void jailCreateBase(
    final Path base_archive,
    final JailArchiveFormat format,
    final Path base,
    final Path base_template)
    throws IOException
  {
    NullCheck.notNull(base_archive, "Base archive");
    NullCheck.notNull(format, "Format");
    NullCheck.notNull(base, "Base");
    NullCheck.notNull(base_template, "Base template");

    if (Files.exists(base)) {
      throw new FileAlreadyExistsException(base.toString());
    }
    if (Files.exists(base_template)) {
      throw new FileAlreadyExistsException(base_template.toString());
    }

    this.jailUnpackArchive(base_archive, format, base);
    this.jailCreateBaseTemplate(base, base_template);
  }

  private void jailCreateBaseTemplate(
    final Path base,
    final Path base_template)
    throws IOException
  {
    this.jailCreateBaseTemplateDirectories(base_template);
    this.jailCreateBaseTemplateMoveMutableParts(base, base_template);
    this.jailCreateBaseTemplateSymlinks(base, base_template);
  }

  private void jailCreateBaseTemplateSymlinks(
    final Path base,
    final Path base_template)
    throws IOException
  {
    final FileSystem base_fs = base.getFileSystem();
    for (final String name : JAIL_TEMPLATE_LINKS) {
      final Path link_target =
        base_fs.getPath("/", base.getFileName().toString(), name);
      final Path link_name = base_template.resolve(name);
      LOG.debug("symlink {} → {}", link_name, link_target);
      Files.createSymbolicLink(link_name, link_target);
    }
  }

  private void jailCreateBaseTemplateMoveMutableParts(
    final Path base,
    final Path base_template)
    throws IOException
  {
    final List<String> directories =
      List.of("etc", "var");

    for (final String directory : directories) {
      final Path source = base.resolve(directory);
      final Path target = base_template.resolve(directory);
      LOG.debug("move {} → {}", source, target);
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }
  }

  private void jailCreateBaseTemplateDirectories(final Path base_template)
    throws IOException
  {
    final List<String> directories = List.of("dev", "proc", "tmp", "usr");
    for (final String directory : directories) {
      final Path target = base_template.resolve(directory);
      LOG.debug("create {}", target);
      Files.createDirectories(target);
    }
  }

  @Override
  public void jailUnpackArchive(
    final Path base_archive,
    final JailArchiveFormat format,
    final Path base)
    throws IOException
  {
    NullCheck.notNull(base_archive, "Base archive");
    NullCheck.notNull(format, "Format");
    NullCheck.notNull(base, "Base");

    LOG.debug("unpack {} ({}) -> {}", base_archive, format, base);

    try (final BufferedInputStream stream =
           new BufferedInputStream(Files.newInputStream(base_archive))) {
      switch (format) {
        case JAIL_ARCHIVE_FORMAT_TAR_XZ: {
          this.jailUnpackArchiveTarXZ(base_archive, stream, base);
          return;
        }
      }
    }
  }

  private void check(
    final String function,
    final String name,
    final String message,
    final int code,
    final int errno)
    throws IOException
  {
    if (code == -1) {
      final StringBuilder sb = new StringBuilder(128);
      sb.append(message);
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
      throw new IOException(sb.toString());
    }
  }

  private void jailUnpackArchiveTarXZ(
    final Path base_archive,
    final BufferedInputStream stream,
    final Path base)
    throws IOException
  {
    try (final XZCompressorInputStream stream_xz =
           new XZCompressorInputStream(stream)) {

      try (final TarArchiveInputStream stream_tar =
             new TarArchiveInputStream(stream_xz)) {

        while (true) {
          final TarArchiveEntry entry = stream_tar.getNextTarEntry();
          if (entry == null) {
            return;
          }

          final long uid = entry.getLongUserId();
          final long gid = entry.getLongGroupId();
          final int mode = entry.getMode();
          final String name = entry.getName();

          if (!entry.isCheckSumOK()) {
            LOG.warn("incorrect checksum for {}", name);
          }

          String target = null;
          FileKind kind = null;
          if (entry.isFile()) {
            kind = FileKind.FILE;
          } else if (entry.isDirectory()) {
            kind = FileKind.DIRECTORY;
          } else if (entry.isSymbolicLink()) {
            target = entry.getLinkName();
            kind = FileKind.SYMBOLIC_LINK;
          }

          this.jailUnpackFile(
            base,
            stream_tar,
            uid,
            gid,
            mode,
            name,
            target,
            NullCheck.notNull(kind, "File kind"));
        }
      }
    }
  }

  private enum FileKind
  {
    FILE,
    DIRECTORY,
    SYMBOLIC_LINK
  }

  private void jailUnpackFile(
    final Path base,
    final InputStream stream,
    final long uid,
    final long gid,
    final int mode,
    final String name,
    final @Nullable String target,
    final FileKind kind)
    throws IOException
  {
    final Path path = base.resolve(name).toAbsolutePath();

    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "unpack {} {} → {} (uid {} gid {} mode {})",
        kind,
        name,
        path,
        Long.valueOf(uid),
        Long.valueOf(gid),
        Integer.toOctalString(mode));
    }

    switch (kind) {
      case FILE: {
        Files.createDirectories(path.getParent());
        Files.copy(stream, path);
        break;
      }
      case DIRECTORY: {
        Files.createDirectories(path);
        break;
      }
      case SYMBOLIC_LINK: {
        final Path resolve = path.resolve(target);
        LOG.debug("link target: {}", resolve);
        Files.createDirectories(path.getParent());
        Files.createSymbolicLink(path, resolve);
        break;
      }
    }

    final String path_s = path.toString();

    switch (kind) {
      case FILE:
      case DIRECTORY: {
        {
          final int r = this.posix.chown(
            path_s,
            (int) uid,
            (int) gid);
          final int errno = this.posix.errno();
          this.check("chown", path_s, "Could not set owner", r, errno);
        }

        {
          final int r = this.posix.chmod(path_s, mode);
          final int errno = this.posix.errno();
          this.check("chmod", path_s, "Could not set mode", r, errno);
        }
        break;
      }
      case SYMBOLIC_LINK: {
        {
          final int r = this.posix.lchown(
            path_s,
            (int) uid,
            (int) gid);
          final int errno = this.posix.errno();
          this.check("lchown", path_s, "Could not set link owner", r, errno);
        }

        {
          final int r = this.posix.lchmod(path_s, mode);
          final int errno = this.posix.errno();
          this.check("lchmod", path_s, "Could not set link mode", r, errno);
        }
        break;
      }
    }
  }

  private Path download(
    final BooleanSupplier is_cancelled,
    final Path file,
    final URI base,
    final String arch,
    final String release,
    final String archive_file,
    final Optional<JailDownloadProgressType> progress)
    throws IOException
  {
    final StringBuilder sb = new StringBuilder(128);
    sb.append(base);
    sb.append("/");
    sb.append(arch);
    sb.append("/");
    sb.append(release);
    sb.append("/");
    sb.append(archive_file);

    final URI uri;

    try {
      uri = new URI(sb.toString());
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }

    try (final CloseableHttpClient client =
           NullCheck.notNull(this.clients.get(), "Client")) {

      final long bytes_total_expected =
        downloadGetTotalExpectedBytes(is_cancelled, client, uri);

      final long bytes_starting;
      if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
        bytes_starting = Files.size(file);
        if (LOG.isDebugEnabled()) {
          LOG.debug(
            "file exists and is {} octets",
            Long.valueOf(bytes_starting));
        }
      } else {
        bytes_starting = 0L;
        LOG.debug("file does not exist");
      }

      LOG.debug("opening {} for writing", file);
      try (final OutputStream output =
             Files.newOutputStream(
               file,
               StandardOpenOption.APPEND,
               StandardOpenOption.CREATE)) {
        downloadToFile(
          is_cancelled,
          client,
          uri,
          output,
          bytes_starting,
          bytes_total_expected,
          progress);
      }
    }

    return file;
  }

  private static long downloadGetTotalExpectedBytes(
    final BooleanSupplier is_cancelled,
    final CloseableHttpClient client,
    final URI uri)
    throws IOException
  {
    if (is_cancelled.getAsBoolean()) {
      throw new CancellationException();
    }

    LOG.debug("HEAD {}", uri);

    final HttpUriRequest request = new HttpHead(uri);
    try (final CloseableHttpResponse response = client.execute(request)) {
      final StatusLine status = response.getStatusLine();
      final int code = status.getStatusCode();

      if (LOG.isDebugEnabled()) {
        LOG.debug("HEAD {} returned {}", uri, Integer.valueOf(code));
      }

      if (code >= 400) {
        throw httpError(
          "Server returned an error when checking the remote file size.",
          response);
      }

      final Header[] headers = response.getHeaders("Content-Length");
      if (headers != null && headers.length > 0) {
        try {
          return Long.parseUnsignedLong(headers[0].getValue());
        } catch (final NumberFormatException e) {
          LOG.error("unparseable Content-Length: ", e);
        }
      }

      throw httpError(
        "Server did not return a usable Content-Length when checking the remote file size.",
        response);
    }
  }

  private static IOException httpError(
    final String message,
    final HttpResponse response)
  {
    final StatusLine status = response.getStatusLine();
    final int code = status.getStatusCode();

    final StringBuilder sb = new StringBuilder(128);
    sb.append(message);
    sb.append(System.lineSeparator());
    sb.append("  Status: ");
    sb.append(status.getReasonPhrase());
    sb.append(System.lineSeparator());
    sb.append("  Status code: ");
    sb.append(code);
    sb.append(System.lineSeparator());
    return new IOException(sb.toString());
  }

  private static void downloadToFile(
    final BooleanSupplier is_cancelled,
    final CloseableHttpClient client,
    final URI uri,
    final OutputStream output,
    final long bytes_starting,
    final long bytes_total_expected,
    final Optional<JailDownloadProgressType> progress)
    throws IOException
  {
    if (is_cancelled.getAsBoolean()) {
      throw new CancellationException();
    }

    if (bytes_starting == bytes_total_expected) {
      LOG.debug("file already completely downloaded");
      return;
    }

    LOG.debug(
      "GET {} (starting at {} bytes)",
      uri,
      Long.valueOf(bytes_starting));

    final HttpUriRequest request = new HttpGet(uri);
    request.addHeader(
      "Range",
      String.format("bytes=%d-", Long.valueOf(bytes_starting)));

    try (final CloseableHttpResponse response = client.execute(request)) {
      final StatusLine status = response.getStatusLine();
      final int code = status.getStatusCode();
      if (code >= 400) {
        throw httpError(
          "Server returned an error when attempting to retrieve the file.",
          response);
      }

      final HttpEntity entity = response.getEntity();
      if (entity == null) {
        throw httpError(
          "Server failed to return a usable HTTP entity.",
          response);
      }

      final long bytes_now_expected = entity.getContentLength();
      try (final InputStream stream =
             NullCheck.notNull(entity.getContent(), "Entity content")) {
        final byte[] buffer = new byte[4096];
        long bytes_now_received = 0L;
        while (true) {
          if (is_cancelled.getAsBoolean()) {
            throw new CancellationException();
          }

          final int r = stream.read(buffer);
          if (r == -1) {
            break;
          }

          output.write(buffer, 0, r);

          bytes_now_received = Math.addExact(bytes_now_received, (long) r);
          final long bytes_received_now = bytes_now_received;
          progress.ifPresent(callback -> callback.onProgress(
            bytes_total_expected,
            bytes_received_now));
        }

        final long bytes_total_received =
          Math.addExact(bytes_starting, bytes_now_received);

        if (bytes_total_received != bytes_total_expected) {
          final StringBuilder sb = new StringBuilder(128);
          sb.append("Downloaded file was truncated.");
          sb.append(System.lineSeparator());
          sb.append("  Expected (total): ");
          sb.append(bytes_total_expected);
          sb.append(" octets");
          sb.append(System.lineSeparator());
          sb.append("  Received (total): ");
          sb.append(bytes_total_received);
          sb.append(" octets");
          sb.append(System.lineSeparator());
          sb.append("  Expected (now): ");
          sb.append(bytes_now_expected);
          sb.append(" octets");
          sb.append(System.lineSeparator());
          sb.append("  Received (now): ");
          sb.append(bytes_now_received);
          sb.append(" octets");
          sb.append(System.lineSeparator());
          sb.append(System.lineSeparator());
          throw new IOException(sb.toString());
        }
      }
    }
  }

  /**
   * @return A supplier that yields a default HTTP client implementation
   */

  public static Supplier<CloseableHttpClient> clients()
  {
    return HttpClients::createSystem;
  }
}
