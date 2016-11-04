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

import com.io7m.jguard.jailbuild.api.JailBuildType;
import com.io7m.jguard.jailbuild.api.JailDownloadProgressType;
import com.io7m.jnull.NullCheck;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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

  private JailBuild(
    final Supplier<CloseableHttpClient> in_clients,
    final ExecutorService in_pool)
  {
    this.pool = NullCheck.notNull(in_pool, "Pool");
    this.clients = NullCheck.notNull(in_clients, "in_clients");
  }

  /**
   * @param in_clients An HTTP client supplier
   * @param in_pool    An executor service pool
   *
   * @return A jail builder API
   */

  public static JailBuildType get(
    final Supplier<CloseableHttpClient> in_clients,
    final ExecutorService in_pool)
  {
    return new JailBuild(in_clients, in_pool);
  }

  @Override
  public CompletableFuture<Path> jailDownloadBinaryArchive(
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

    final CompletableFuture<Path> future = new CompletableFuture<>();
    final BooleanSupplier is_cancelled = future::isCancelled;

    this.pool.submit(() -> {
      try {
        future.complete(this.download(
          is_cancelled, file, base, arch, release, archive_file, progress));
      } catch (final Throwable e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  @Override
  public Path jailDownloadBinaryArchiveSync(
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

    return this.download(
      () -> false, file, base, arch, release, archive_file, progress);
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
