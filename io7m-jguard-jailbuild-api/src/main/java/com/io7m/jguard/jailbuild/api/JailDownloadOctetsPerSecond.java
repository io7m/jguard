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

import com.io7m.jnull.NullCheck;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A progress indicator that calculates the number of octets received per
 * second.
 */

public final class JailDownloadOctetsPerSecond implements
  JailDownloadProgressType
{
  private final Clock clock;
  private Instant time_start;
  private final JailDownloadOctetsPerSecondProgressType progress;
  private long count;
  private long total_received_previous;

  /**
   * @param in_progress A progress receiver
   * @param in_clock    A clock
   *
   * @return A progress indicator
   */

  public static JailDownloadProgressType get(
    final JailDownloadOctetsPerSecondProgressType in_progress,
    final Clock in_clock)
  {
    return new JailDownloadOctetsPerSecond(in_progress, in_clock);
  }

  /**
   * A receiver for the number of octets per second.
   */

  public interface JailDownloadOctetsPerSecondProgressType
  {
    /**
     * Called when the number of octets per second is calculated.
     *
     * @param total_expected    The total number of expected octets
     * @param total_received    The total number of received octets
     * @param octets_per_second The current number of octets per second
     */

    void onProgressOctetsPerSecond(
      final long total_expected,
      final long total_received,
      final long octets_per_second);
  }

  private JailDownloadOctetsPerSecond(
    final JailDownloadOctetsPerSecondProgressType in_progress,
    final Clock in_clock)
  {
    this.clock = NullCheck.notNull(in_clock, "Clock");
    this.progress = NullCheck.notNull(in_progress, "Progress");
    this.time_start = this.clock.instant();
    this.total_received_previous = 0L;
    this.count = 0L;
  }

  @Override
  public void onProgress(
    final long total_expected,
    final long total_received)
  {
    final long received =
      Math.subtractExact(total_received, this.total_received_previous);

    final Instant time_now = this.clock.instant();
    this.count = Math.addExact(this.count, received);
    if (ChronoUnit.SECONDS.between(this.time_start, time_now) >= 1L) {
      this.progress.onProgressOctetsPerSecond(
        total_expected,
        total_received,
        this.count);
      this.count = 0L;
      this.time_start = time_now;
    }

    this.total_received_previous = total_received;
  }
}
