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

package com.io7m.jguard.jailbuild.api;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An API for building jails.
 */

public interface JailBuildType
{
  /**
   * Download a binary archive from a FreeBSD mirror. The download is performed
   * asynchronously and may be controlled by the returned future.
   *
   * @param file         The output file
   * @param base         The base URI
   * @param arch         The architecture of the release (such as {@code
   *                     amd64})
   * @param release      The release (such as {@code 10.3-RELEASE}).
   * @param archive_file The archive file (such as {@code base.txz})
   * @param progress     An optional consumer of download progress information
   *
   * @return A future representing the download in progress
   */

  CompletableFuture<Path> jailDownloadBinaryArchive(
    Path file,
    URI base,
    String arch,
    String release,
    String archive_file,
    Optional<JailDownloadProgressType> progress);

  /**
   * Download a binary archive from a FreeBSD mirror. The download is performed
   * synchronously on the calling thread.
   *
   * @param file         The output file
   * @param base         The base URI
   * @param arch         The architecture of the release (such as {@code
   *                     amd64})
   * @param release      The release (such as {@code 10.3-RELEASE}).
   * @param archive_file The archive file (such as {@code base.txz})
   * @param progress     An optional consumer of download progress information
   *
   * @return The path to the downloaded file when the download is completed
   *
   * @throws IOException On any error
   */

  Path jailDownloadBinaryArchiveSync(
    Path file,
    URI base,
    String arch,
    String release,
    String archive_file,
    Optional<JailDownloadProgressType> progress)
    throws IOException;
}
