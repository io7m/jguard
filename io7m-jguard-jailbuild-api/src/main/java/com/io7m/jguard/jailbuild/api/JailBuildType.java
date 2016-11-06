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

import com.io7m.jguard.core.JailConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * An API for building jails.
 */

public interface JailBuildType
{
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
   * @throws IOException On any error
   */

  void jailDownloadBinaryArchive(
    Path file,
    URI base,
    String arch,
    String release,
    String archive_file,
    Optional<JailDownloadProgressType> progress)
    throws IOException;

  /**
   * <p>Unpack {@code base_archive} into {@code base} and then create the base
   * template directory {@code base_template}.</p>
   *
   * <p>The directories {@code base} and {@code base_template} must not
   * exist.</p>
   *
   * @param base_archive  The base archive (such as {@code base.txz})
   * @param format        The archive format
   * @param base          The base directory
   * @param base_template The base template for new jails
   *
   * @throws FileAlreadyExistsException If {@code base} or {@code base_template}
   *                                    already exist
   * @throws IOException                On any error
   */

  void jailCreateBase(
    Path base_archive,
    JailArchiveFormat format,
    Path base,
    Path base_template)
    throws IOException, FileAlreadyExistsException;

  /**
   * Unpack the given archive.
   *
   * @param base_archive The archive
   * @param format       The archive format
   * @param base         The base directory
   *
   * @throws IOException On errors
   */

  void jailUnpackArchive(
    Path base_archive,
    JailArchiveFormat format,
    Path base)
    throws IOException;

  /**
   * Create a new jail.
   *
   * @param base          The base directory that will be mounted inside the
   *                      jail
   * @param base_template The template directory
   * @param config        The jail configuration
   *
   * @throws NotDirectoryException      If {@code base} is not a directory
   * @throws NotDirectoryException      If {@code base_template} is not a
   *                                    directory
   * @throws FileAlreadyExistsException If a jail already exists with the given
   *                                    name
   * @throws IOException                On errors
   */

  void jailCreate(
    Path base,
    Path base_template,
    JailConfiguration config)
    throws IOException,
    FileAlreadyExistsException,
    NotDirectoryException;
}
