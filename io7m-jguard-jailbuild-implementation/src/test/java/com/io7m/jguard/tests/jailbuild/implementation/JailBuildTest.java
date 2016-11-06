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

package com.io7m.jguard.tests.jailbuild.implementation;

import com.io7m.jguard.core.JailConfiguration;
import com.io7m.jguard.core.JailName;
import com.io7m.jguard.jailbuild.api.JailArchiveFormat;
import com.io7m.jguard.jailbuild.api.JailBuildType;
import com.io7m.jguard.jailbuild.api.JailDownloadOctetsPerSecond;
import com.io7m.jguard.jailbuild.api.JailDownloadProgressType;
import com.io7m.jguard.jailbuild.implementation.JailBuild;
import javaslang.collection.HashMap;
import javaslang.collection.List;
import javaslang.collection.Map;
import jnr.posix.FileStat;
import jnr.posix.POSIX;
import mockit.Mock;
import mockit.MockUp;
import mockit.StrictExpectations;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.Is;
import org.hamcrest.core.StringStartsWith;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class JailBuildTest
{
  private static final JailDownloadProgressType PROGRESS;
  private static final URI BASE_URI;
  private static final Logger LOG;

  static {
    BASE_URI =
      URI.create("http://ftp.freebsd.org/pub/FreeBSD/releases/");

    PROGRESS =
      JailDownloadOctetsPerSecond.get(
        (total_expected, total_received, octets_per_second) -> {
        },
        Clock.systemUTC());

    LOG = LoggerFactory.getLogger(JailBuildTest.class);
  }

  @Rule public ExpectedException expected = ExpectedException.none();

  private FileSystem filesystem;

  @Before
  public void onSetup()
    throws Exception
  {
    this.filesystem = TestFilesystems.makeEmptyUnixFilesystem();
  }

  @After
  public void onTearDown()
    throws Exception
  {
    this.filesystem.close();
  }

  @Test
  public void testDownloadSyncBadURI()
    throws Exception
  {
    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    final Path file =
      this.filesystem.getPath("/base.txz");

    this.expected.expect(IllegalArgumentException.class);
    this.expected.expectCause(Is.isA(URISyntaxException.class));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "file with spaces",
      Optional.of(PROGRESS));
  }

  @Test
  public void testDownloadSyncHead404()
    throws Exception
  {
    final URI expected_uri =
      URI.create(BASE_URI + "/amd64/10.3-RELEASE/base.txz");

    final MockUp<CloseableHttpResponse> mock_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 404, "Not found");
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          Assert.assertThat(request, MatchesRequest.of("HEAD", expected_uri));
          return mock_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith(
        "Server returned an error when checking the remote file size."));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public void testDownloadSyncHeadGibberish0()
    throws Exception
  {
    final URI expected_uri =
      URI.create(BASE_URI + "/amd64/10.3-RELEASE/base.txz");

    final MockUp<CloseableHttpResponse> mock_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[0];
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          Assert.assertThat(request, MatchesRequest.of("HEAD", expected_uri));
          return mock_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith(
        "Server did not return a usable Content-Length when checking the remote file size."));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public void testDownloadSyncHeadGibberish1()
    throws Exception
  {
    final URI expected_uri =
      URI.create(BASE_URI + "/amd64/10.3-RELEASE/base.txz");

    final MockUp<CloseableHttpResponse> mock_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "Nonsense")
          };
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          Assert.assertThat(request, MatchesRequest.of("HEAD", expected_uri));
          return mock_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith(
        "Server did not return a usable Content-Length when checking the remote file size."));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  private static final class MatchesRequest
    extends TypeSafeMatcher<HttpUriRequest>
  {
    private final URI remote_uri;
    private final String method;

    MatchesRequest(
      final String method,
      final URI remote_uri)
    {
      this.method = method;
      this.remote_uri = remote_uri;
    }

    public static TypeSafeMatcher<HttpUriRequest> of(
      final String method,
      final URI remote_uri)
    {
      return new MatchesRequest(method, remote_uri);
    }

    @Override
    protected boolean matchesSafely(final HttpUriRequest item)
    {
      return Objects.equals(this.method, item.getMethod()) &&
        Objects.equals(this.remote_uri, item.getURI());
    }

    @Override
    public void describeTo(final Description description)
    {
      description.appendText("method is " + this.method);
      description.appendText("URI is " + this.remote_uri);
    }
  }

  @Test
  public void testDownloadGet404()
    throws Exception
  {
    final URI expected_uri =
      URI.create(BASE_URI + "/amd64/10.3-RELEASE/base.txz");

    final MockUp<CloseableHttpResponse> mock_head_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "100")
          };
        }
      };

    final MockUp<CloseableHttpResponse> mock_get_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 404, "Not found");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "100")
          };
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          if ("HEAD".equals(request.getMethod())) {
            return mock_head_response.getMockInstance();
          }
          return mock_get_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith(
        "Server returned an error when attempting to retrieve the file."));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public void testDownloadGetBadContentLength()
    throws Exception
  {
    final URI expected_uri =
      URI.create(BASE_URI + "/amd64/10.3-RELEASE/base.txz");

    final MockUp<CloseableHttpResponse> mock_head_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "100")
          };
        }
      };

    final MockUp<CloseableHttpResponse> mock_get_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "Nonsense")
          };
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          if ("HEAD".equals(request.getMethod())) {
            return mock_head_response.getMockInstance();
          }
          return mock_get_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith("Server failed to return a usable HTTP entity."));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public void testDownloadGetShortData()
    throws Exception
  {
    final URI expected_uri =
      URI.create(BASE_URI + "/amd64/10.3-RELEASE/base.txz");

    final MockUp<CloseableHttpResponse> mock_head_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "100")
          };
        }
      };

    final MockUp<CloseableHttpResponse> mock_get_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "100")
          };
        }

        @Mock
        public HttpEntity getEntity()
        {
          final BasicHttpEntity entity = new BasicHttpEntity();
          entity.setContentLength(32L);
          entity.setContent(new ByteArrayInputStream(new byte[32]));
          return entity;
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          if ("HEAD".equals(request.getMethod())) {
            return mock_head_response.getMockInstance();
          }
          return mock_get_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith("Downloaded file was truncated."));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public void testDownloadGetAllCorrect()
    throws Exception
  {
    final MockUp<CloseableHttpResponse> mock_head_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "32")
          };
        }
      };

    final Random random = new Random();
    final byte[] data = new byte[32];
    random.nextBytes(data);

    final MockUp<CloseableHttpResponse> mock_get_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "32")
          };
        }

        @Mock
        public HttpEntity getEntity()
        {
          final BasicHttpEntity entity = new BasicHttpEntity();
          entity.setContentLength(32L);
          entity.setContent(new ByteArrayInputStream(data));
          return entity;
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          if ("HEAD".equals(request.getMethod())) {
            return mock_head_response.getMockInstance();
          }
          return mock_get_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    final Path file = this.filesystem.getPath("/base.txz");
    Assert.assertFalse(Files.exists(file));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));

    final byte[] received_data = Files.readAllBytes(file);
    Assert.assertArrayEquals(data, received_data);
  }

  @Test
  public void testDownloadGetResume()
    throws Exception
  {
    final Random random = new Random();
    final byte[] data = new byte[32];
    random.nextBytes(data);

    final Path file = this.filesystem.getPath("/base.txz");

    try (final OutputStream output = Files.newOutputStream(file)) {
      output.write(data, 0, 16);
      output.flush();
    }

    Assert.assertEquals(16L, Files.size(file));

    final MockUp<CloseableHttpResponse> mock_head_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "32")
          };
        }
      };

    final MockUp<CloseableHttpResponse> mock_get_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "16")
          };
        }

        @Mock
        public HttpEntity getEntity()
        {
          final BasicHttpEntity entity = new BasicHttpEntity();
          entity.setContentLength(16L);
          entity.setContent(new ByteArrayInputStream(data, 16, 16));
          return entity;
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          if ("HEAD".equals(request.getMethod())) {
            return mock_head_response.getMockInstance();
          }
          return mock_get_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));

    Assert.assertEquals(32L, Files.size(file));
    final byte[] received_data = Files.readAllBytes(file);
    Assert.assertArrayEquals(data, received_data);
  }

  @Test
  public void testDownloadGetGotAlready()
    throws Exception
  {
    final Random random = new Random();
    final byte[] data = new byte[32];
    random.nextBytes(data);

    final Path file = this.filesystem.getPath("/base.txz");
    try (final OutputStream output = Files.newOutputStream(file)) {
      output.write(data);
      output.flush();
    }

    Assert.assertEquals(32L, Files.size(file));

    final MockUp<CloseableHttpResponse> mock_head_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }

        @Mock
        public Header[] getHeaders(final String name)
        {
          return new Header[]{
            new BasicHeader("Content-Length", "32")
          };
        }
      };

    final MockUp<CloseableHttpResponse> mock_get_response =
      new MockUp<CloseableHttpResponse>()
      {
        @Mock
        public StatusLine getStatusLine()
        {
          return new BasicStatusLine(
            new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        }
      };

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
        @Mock
        public CloseableHttpResponse execute(
          final HttpUriRequest request)
          throws IOException, ClientProtocolException
        {
          if ("HEAD".equals(request.getMethod())) {
            return mock_head_response.getMockInstance();
          }
          return mock_get_response.getMockInstance();
        }
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    Assert.assertEquals(32L, Files.size(file));

    build.jailDownloadBinaryArchive(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));

    Assert.assertEquals(32L, Files.size(file));
    final byte[] received_data = Files.readAllBytes(file);
    Assert.assertArrayEquals(data, received_data);
  }

  @Test
  public void testUnpackArchiveOK()
    throws Exception
  {
    final Path archive_file =
      this.filesystem.getPath("/base.txz");
    final Path path =
      this.filesystem.getPath("/base");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
        @Mock
        int errno()
        {
          return 0;
        }

        @Mock
        int chown(
          final String name,
          final int uid,
          final int gid)
        {
          LOG.debug(
            "chown: {} {} {}",
            name,
            Integer.valueOf(uid),
            Integer.valueOf(gid));
          return 0;
        }

        @Mock
        int lchown(
          final String name,
          final int uid,
          final int gid)
        {
          LOG.debug(
            "lchown: {} {} {}",
            name,
            Integer.valueOf(uid),
            Integer.valueOf(gid));
          return 0;
        }

        @Mock
        int chmod(
          final String name,
          final int mode)
        {
          LOG.debug(
            "chmod: {} {}",
            name,
            Integer.valueOf(mode));

          final String ps;
          final Path actual = path.resolve(name);
          if (mode == 0755) {
            ps = "rwxr-xr-x";
          } else if (mode == 0644) {
            ps = "rw-r--r--";
          } else {
            ps = "---------";
          }

          try {
            Files.setPosixFilePermissions(
              actual, PosixFilePermissions.fromString(ps));
          } catch (final IOException e) {
            LOG.error("failed to set file permissions: ", e);
            return -1;
          }

          return 0;
        }

        @Mock
        int lchmod(
          final String name,
          final int mode)
        {
          LOG.debug(
            "lchmod: {} {}",
            name,
            Integer.valueOf(mode));
          return 0;
        }

      }.getMockInstance();

    final MockUp<Files> mock_files =
      new MockUp<Files>()
      {
        @Mock
        public InputStream newInputStream(
          final Path path,
          final OpenOption... options)
          throws IOException
        {
          LOG.debug("newInputStream: {}", path);
          return new MockUp<InputStream>()
          {

          }.getMockInstance();
        }
      };

    final MockUp<XZCompressorInputStream> mock_xz =
      new MockUp<XZCompressorInputStream>()
      {
        public InputStream inner;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("XZCompressorInputStream: {}", stream);
          this.inner = stream;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "XZCompressorInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));
          return this.inner.read(buf, offset, r);
        }

        @Mock
        void close()
        {
          LOG.debug("XZCompressorInputStream: close");
        }
      };

    final MockUp<TarArchiveInputStream> mock_tar =
      new MockUp<TarArchiveInputStream>()
      {
        private int entry_index;
        private int read_count;
        private List<TarArchiveEntry> entries;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("TarArchiveInputStream: {}", stream);

          final TarArchiveEntry entry_file = new TarArchiveEntry("file");
          entry_file.setGroupId(100);
          entry_file.setUserId(100);
          entry_file.setMode(0644);
          entry_file.setSize(4);

          final TarArchiveEntry entry_dir = new TarArchiveEntry("directory/");
          entry_dir.setGroupId(100);
          entry_dir.setUserId(100);
          entry_dir.setMode(0755);

          final TarArchiveEntry entry_link = new TarArchiveEntry("link") {
            @Override
            public boolean isFile()
            {
              return false;
            }

            @Override
            public String getLinkName()
            {
              return "/target";
            }

            @Override
            public boolean isSymbolicLink()
            {
              return true;
            }
          };
          entry_link.setGroupId(100);
          entry_link.setUserId(100);
          entry_link.setMode(0777);
          entry_link.setSize(4);

          this.entries = List.of(entry_file, entry_dir, entry_link);
        }

        @Mock
        public TarArchiveEntry getNextTarEntry()
          throws IOException
        {
          if (this.entry_index < this.entries.size()) {
            final TarArchiveEntry entry = this.entries.get(this.entry_index);
            ++this.entry_index;
            return entry;
          }
          return null;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "TarArchiveInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));

          try {
            if (this.read_count == 0) {
              Assert.assertTrue(buf.length >= 4);
              buf[0] = (byte) 0x0;
              buf[1] = (byte) 0x1;
              buf[2] = (byte) 0x2;
              buf[3] = (byte) 0x3;
              return 4;
            }

            return 0;
          } finally {
            ++this.read_count;
          }
        }

        @Mock
        void close()
        {
          LOG.debug("TarArchiveInputStream: close");
        }
      };

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    build.jailUnpackArchive(
      archive_file,
      JailArchiveFormat.JAIL_ARCHIVE_FORMAT_TAR_XZ,
      path);

    Assert.assertTrue(Files.isDirectory(path));
    Assert.assertTrue(Files.isRegularFile(path.resolve("file")));
    Assert.assertTrue(Files.isDirectory(path.resolve("directory")));
    Assert.assertTrue(Files.isSymbolicLink(path.resolve("link")));

    {
      final byte[] data = Files.readAllBytes(path.resolve("file"));
      Assert.assertArrayEquals(
        new byte[]{
          (byte) 0x0,
          (byte) 0x1,
          (byte) 0x2,
          (byte) 0x3
        }, data);

      final Set<PosixFilePermission> perms =
        Files.getPosixFilePermissions(path.resolve("file"));
      Assert.assertEquals("rw-r--r--", PosixFilePermissions.toString(perms));
    }

    {
      final Set<PosixFilePermission> perms =
        Files.getPosixFilePermissions(path.resolve("directory"));
      Assert.assertEquals("rwxr-xr-x", PosixFilePermissions.toString(perms));
    }

    {
      Assert.assertEquals("/target", Files.readSymbolicLink(path.resolve("link")).toString());
    }
  }

  @Test
  public void testUnpackArchiveChmodFailed()
    throws Exception
  {
    final Path archive_file =
      this.filesystem.getPath("/base.txz");
    final Path path =
      this.filesystem.getPath("/base");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
        @Mock
        int errno()
        {
          return 5;
        }

        @Mock
        int chown(
          final String name,
          final int uid,
          final int gid)
        {
          LOG.debug(
            "chown: {} {} {}",
            name,
            Integer.valueOf(uid),
            Integer.valueOf(gid));
          return 0;
        }

        @Mock
        int chmod(
          final String name,
          final int mode)
        {
          return -1;
        }

      }.getMockInstance();

    final MockUp<Files> mock_files =
      new MockUp<Files>()
      {
        @Mock
        public InputStream newInputStream(
          final Path path,
          final OpenOption... options)
          throws IOException
        {
          LOG.debug("newInputStream: {}", path);
          return new MockUp<InputStream>()
          {

          }.getMockInstance();
        }
      };

    final MockUp<XZCompressorInputStream> mock_xz =
      new MockUp<XZCompressorInputStream>()
      {
        public InputStream inner;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("XZCompressorInputStream: {}", stream);
          this.inner = stream;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "XZCompressorInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));
          return this.inner.read(buf, offset, r);
        }

        @Mock
        void close()
        {
          LOG.debug("XZCompressorInputStream: close");
        }
      };

    final MockUp<TarArchiveInputStream> mock_tar =
      new MockUp<TarArchiveInputStream>()
      {
        private int entry_index;
        private int read_count;
        private List<TarArchiveEntry> entries;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("TarArchiveInputStream: {}", stream);

          final TarArchiveEntry entry_file = new TarArchiveEntry("file");
          entry_file.setGroupId(100);
          entry_file.setUserId(100);
          entry_file.setMode(0644);
          entry_file.setSize(4);

          this.entries = List.of(entry_file);
        }

        @Mock
        public TarArchiveEntry getNextTarEntry()
          throws IOException
        {
          if (this.entry_index < this.entries.size()) {
            final TarArchiveEntry entry = this.entries.get(this.entry_index);
            ++this.entry_index;
            return entry;
          }
          return null;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "TarArchiveInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));

          try {
            if (this.read_count == 0) {
              Assert.assertTrue(buf.length >= 4);
              buf[0] = (byte) 0x0;
              buf[1] = (byte) 0x1;
              buf[2] = (byte) 0x2;
              buf[3] = (byte) 0x3;
              return 4;
            }

            return 0;
          } finally {
            ++this.read_count;
          }
        }

        @Mock
        void close()
        {
          LOG.debug("TarArchiveInputStream: close");
        }
      };

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    this.expected.expect(IOException.class);
    this.expected.expectMessage(new StringStartsWith("Could not set mode"));

    build.jailUnpackArchive(
      archive_file,
      JailArchiveFormat.JAIL_ARCHIVE_FORMAT_TAR_XZ,
      path);
  }

  @Test
  public void testUnpackArchiveChownFailed()
    throws Exception
  {
    final Path archive_file =
      this.filesystem.getPath("/base.txz");
    final Path path =
      this.filesystem.getPath("/base");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {
        @Mock
        int errno()
        {
          return 5;
        }

        @Mock
        int chown(
          final String name,
          final int uid,
          final int gid)
        {
          return -1;
        }

        @Mock
        int chmod(
          final String name,
          final int mode)
        {
          LOG.debug(
            "chmod: {} {}",
            name,
            Integer.valueOf(mode));

          final String ps;
          final Path actual = path.resolve(name);
          if (mode == 0755) {
            ps = "rwxr-xr-x";
          } else if (mode == 0644) {
            ps = "rw-r--r--";
          } else {
            ps = "---------";
          }

          try {
            Files.setPosixFilePermissions(
              actual, PosixFilePermissions.fromString(ps));
          } catch (final IOException e) {
            return -1;
          }

          return 0;
        }

      }.getMockInstance();

    final MockUp<Files> mock_files =
      new MockUp<Files>()
      {
        @Mock
        public InputStream newInputStream(
          final Path path,
          final OpenOption... options)
          throws IOException
        {
          LOG.debug("newInputStream: {}", path);
          return new MockUp<InputStream>()
          {

          }.getMockInstance();
        }
      };

    final MockUp<XZCompressorInputStream> mock_xz =
      new MockUp<XZCompressorInputStream>()
      {
        public InputStream inner;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("XZCompressorInputStream: {}", stream);
          this.inner = stream;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "XZCompressorInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));
          return this.inner.read(buf, offset, r);
        }

        @Mock
        void close()
        {
          LOG.debug("XZCompressorInputStream: close");
        }
      };

    final MockUp<TarArchiveInputStream> mock_tar =
      new MockUp<TarArchiveInputStream>()
      {
        private int entry_index;
        private int read_count;
        private List<TarArchiveEntry> entries;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("TarArchiveInputStream: {}", stream);

          final TarArchiveEntry entry_file = new TarArchiveEntry("file");
          entry_file.setGroupId(100);
          entry_file.setUserId(100);
          entry_file.setMode(0644);
          entry_file.setSize(4);

          this.entries = List.of(entry_file);
        }

        @Mock
        public TarArchiveEntry getNextTarEntry()
          throws IOException
        {
          if (this.entry_index < this.entries.size()) {
            final TarArchiveEntry entry = this.entries.get(this.entry_index);
            ++this.entry_index;
            return entry;
          }
          return null;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "TarArchiveInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));

          try {
            if (this.read_count == 0) {
              Assert.assertTrue(buf.length >= 4);
              buf[0] = (byte) 0x0;
              buf[1] = (byte) 0x1;
              buf[2] = (byte) 0x2;
              buf[3] = (byte) 0x3;
              return 4;
            }

            return 0;
          } finally {
            ++this.read_count;
          }
        }

        @Mock
        void close()
        {
          LOG.debug("TarArchiveInputStream: close");
        }
      };

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    this.expected.expect(IOException.class);
    this.expected.expectMessage(new StringStartsWith("Could not set owner"));

    build.jailUnpackArchive(
      archive_file,
      JailArchiveFormat.JAIL_ARCHIVE_FORMAT_TAR_XZ,
      path);
  }

  @Test
  public void testCreateBaseOK()
    throws Exception
  {
    final Path archive_file =
      this.filesystem.getPath("/base.txz");
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockPOSIXOps(path, HashMap.empty()).getMockInstance();

    final MockUp<Files> mock_files =
      new MockUp<Files>()
      {
        @Mock
        public InputStream newInputStream(
          final Path path,
          final OpenOption... options)
          throws IOException
        {
          LOG.debug("newInputStream: {}", path);
          return new MockUp<InputStream>()
          {

          }.getMockInstance();
        }
      };

    final MockUp<XZCompressorInputStream> mock_xz =
      new MockUp<XZCompressorInputStream>()
      {
        public InputStream inner;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("XZCompressorInputStream: {}", stream);
          this.inner = stream;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "XZCompressorInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));
          return this.inner.read(buf, offset, r);
        }

        @Mock
        void close()
        {
          LOG.debug("XZCompressorInputStream: close");
        }
      };

    final MockUp<TarArchiveInputStream> mock_tar =
      new MockUp<TarArchiveInputStream>()
      {
        private int entry_index;
        private int read_count;
        private List<TarArchiveEntry> entries;

        @Mock
        void $init(
          final InputStream stream)
        {
          LOG.debug("TarArchiveInputStream: {}", stream);

          final TarArchiveEntry entry_etc = new TarArchiveEntry("etc/");
          entry_etc.setGroupId(0);
          entry_etc.setUserId(0);
          entry_etc.setMode(0755);

          final TarArchiveEntry entry_var = new TarArchiveEntry("var/");
          entry_var.setGroupId(0);
          entry_var.setUserId(0);
          entry_var.setMode(0755);

          this.entries = List.of(entry_etc, entry_var);
        }

        @Mock
        public TarArchiveEntry getNextTarEntry()
          throws IOException
        {
          if (this.entry_index < this.entries.size()) {
            final TarArchiveEntry entry = this.entries.get(this.entry_index);
            ++this.entry_index;
            return entry;
          }
          return null;
        }

        @Mock
        public int read(
          final byte[] buf,
          final int offset,
          final int r)
          throws IOException
        {
          LOG.debug(
            "TarArchiveInputStream: read {} {} {}",
            buf,
            Integer.valueOf(offset),
            Integer.valueOf(r));

          try {
            if (this.read_count == 0) {
              Assert.assertTrue(buf.length >= 4);
              buf[0] = (byte) 0x0;
              buf[1] = (byte) 0x1;
              buf[2] = (byte) 0x2;
              buf[3] = (byte) 0x3;
              return 4;
            }

            return 0;
          } finally {
            ++this.read_count;
          }
        }

        @Mock
        void close()
        {
          LOG.debug("TarArchiveInputStream: close");
        }
      };

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    build.jailCreateBase(
      archive_file,
      JailArchiveFormat.JAIL_ARCHIVE_FORMAT_TAR_XZ,
      path,
      path_template);

    Assert.assertTrue(Files.isDirectory(path));
    Assert.assertTrue(Files.isDirectory(path_template));
    Assert.assertTrue(Files.isDirectory(path_template.resolve("etc")));
    Assert.assertTrue(Files.isDirectory(path_template.resolve("var")));

    Files.walk(path_template).forEach(
      p -> {
        try {
          LOG.debug("path: {}", p);
          if (Files.isSymbolicLink(p)) {
            LOG.debug("path: is symlink {} → {}", p, Files.readSymbolicLink(p));
          }
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      });

    for (final String link : JailBuild.JAIL_TEMPLATE_LINKS) {
      final Path link_path = path_template.resolve(link);
      final Path link_target = path.resolve(link);
      LOG.debug("check: {} -> {}", link_path, link_target);

      Assert.assertTrue(Files.isSymbolicLink(link_path));
      Assert.assertEquals(link_target, Files.readSymbolicLink(link_path));
    }
  }

  @Test
  public void testCreateBaseExists()
    throws Exception
  {
    final Path archive_file =
      this.filesystem.getPath("/base.txz");
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {

      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    Files.createDirectories(path);

    this.expected.expect(FileAlreadyExistsException.class);
    build.jailCreateBase(
      archive_file,
      JailArchiveFormat.JAIL_ARCHIVE_FORMAT_TAR_XZ,
      path,
      path_template);
  }

  @Test
  public void testCreateBaseTemplateExists()
    throws Exception
  {
    final Path archive_file =
      this.filesystem.getPath("/base.txz");
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {

      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    Files.createDirectories(path_template);

    this.expected.expect(FileAlreadyExistsException.class);
    build.jailCreateBase(
      archive_file,
      JailArchiveFormat.JAIL_ARCHIVE_FORMAT_TAR_XZ,
      path,
      path_template);
  }

  @Test
  public void testCreateJailNonexistentBase()
    throws Exception
  {
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {

      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    Files.createDirectories(path_template);

    final JailConfiguration config = JailConfiguration.of(
      this.filesystem.getPath("/jail0"),
      JailName.of("jail0"),
      List.of((Inet4Address) Inet4Address.getByName("10.8.0.23")),
      List.empty(),
      "jail0.example.com",
      List.of("/bin/sh"));

    this.expected.expect(NotDirectoryException.class);
    build.jailCreate(path, path_template, config);
  }

  @Test
  public void testCreateJailNonexistentBaseTemplate()
    throws Exception
  {
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {

      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    Files.createDirectories(path);

    final JailConfiguration config = JailConfiguration.of(
      this.filesystem.getPath("/jail0"),
      JailName.of("jail0"),
      List.of((Inet4Address) Inet4Address.getByName("10.8.0.23")),
      List.empty(),
      "jail0.example.com",
      List.of("/bin/sh"));

    this.expected.expect(NotDirectoryException.class);
    build.jailCreate(path, path_template, config);
  }

  @Test
  public void testCreateJailConfigAlreadyExists()
    throws Exception
  {
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {

      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    Files.createDirectories(path);
    Files.createDirectories(path_template);
    Files.write(this.filesystem.getPath("/jail0.conf"), new byte[] { });

    final JailConfiguration config = JailConfiguration.of(
      this.filesystem.getPath("/jail0"),
      JailName.of("jail0"),
      List.of((Inet4Address) Inet4Address.getByName("10.8.0.23")),
      List.empty(),
      "jail0.example.com",
      List.of("/bin/sh"));

    this.expected.expect(FileAlreadyExistsException.class);
    build.jailCreate(path, path_template, config);
  }

  @Test
  public void testCreateJailFSTabAlreadyExists()
    throws Exception
  {
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final POSIX mock_posix =
      new MockUp<POSIX>()
      {

      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    Files.createDirectories(path);
    Files.createDirectories(path_template);
    Files.write(this.filesystem.getPath("/jail0.fstab"), new byte[] { });

    final JailConfiguration config = JailConfiguration.of(
      this.filesystem.getPath("/jail0"),
      JailName.of("jail0"),
      List.of((Inet4Address) Inet4Address.getByName("10.8.0.23")),
      List.empty(),
      "jail0.example.com",
      List.of("/bin/sh"));

    this.expected.expect(FileAlreadyExistsException.class);
    build.jailCreate(path, path_template, config);
  }

  @Test
  public void testCreateJailOK()
    throws Exception
  {
    final Path path =
      this.filesystem.getPath("/base");
    final Path path_template =
      this.filesystem.getPath("/base-template");

    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final MockUp<FileStat> mock_stats =
      new MockUp<FileStat>()
    {

    };

    final POSIX mock_posix =
      new MockPOSIXOps(path, HashMap.of(
        "/base-template", mock_stats.getMockInstance(),
        "/base-template/file", mock_stats.getMockInstance(),
        "/base-template/link", mock_stats.getMockInstance(),
        "/base-template/dir", mock_stats.getMockInstance(),
        "/base-template/base", mock_stats.getMockInstance()
      )).getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, mock_posix);

    Files.createDirectories(path);
    Files.createDirectories(path_template);
    Files.createFile(path_template.resolve("file"));
    Files.createDirectories(path_template.resolve("dir"));
    Files.createDirectories(path_template.resolve("base"));
    Files.createSymbolicLink(
      path_template.resolve("link"),
      this.filesystem.getPath("/file"));

    final JailConfiguration config = JailConfiguration.of(
      this.filesystem.getPath("/jail0"),
      JailName.of("jail0"),
      List.of((Inet4Address) Inet4Address.getByName("10.8.0.23")),
      List.empty(),
      "jail0.example.com",
      List.of("/bin/sh"));

    build.jailCreate(path, path_template, config);

    Assert.assertTrue(
      Files.isDirectory(this.filesystem.getPath("/jail0")));
    Assert.assertTrue(
      Files.isDirectory(this.filesystem.getPath("/jail0/dir")));
    Assert.assertTrue(
      Files.isDirectory(this.filesystem.getPath("/jail0/base")));
    Assert.assertTrue(
      Files.isRegularFile(this.filesystem.getPath("/jail0/file")));
    Assert.assertTrue(
      Files.isSymbolicLink(this.filesystem.getPath("/jail0/link")));
    Assert.assertEquals(
      this.filesystem.getPath("/file"),
      Files.readSymbolicLink(this.filesystem.getPath("/jail0/link")));
    Assert.assertTrue(
      Files.isRegularFile(this.filesystem.getPath("/jail0.conf")));
    Assert.assertTrue(
      Files.isRegularFile(this.filesystem.getPath("/jail0.fstab")));
  }

  private static class MockPOSIXOps extends MockUp<POSIX>
  {
    private final Path path;
    private final Map<String, FileStat> stats;

    public MockPOSIXOps(
      final Path in_path,
      final Map<String, FileStat> in_stats)
    {
      this.path = in_path;
      this.stats = in_stats;
    }

    @Mock
    int errno()
    {
      return 0;
    }

    @Mock
    public FileStat lstat(
      final String name)
    {
      return this.stats.get(name).getOrElse((FileStat) null);
    }

    @Mock
    public FileStat stat(
      final String name)
    {
      return this.stats.get(name).getOrElse((FileStat) null);
    }

    @Mock
    int chown(
      final String name,
      final int uid,
      final int gid)
    {
      LOG.debug(
        "chown: {} {} {}",
        name,
        Integer.valueOf(uid),
        Integer.valueOf(gid));
      return 0;
    }

    @Mock
    int lchown(
      final String name,
      final int uid,
      final int gid)
    {
      LOG.debug(
        "lchown: {} {} {}",
        name,
        Integer.valueOf(uid),
        Integer.valueOf(gid));
      return 0;
    }

    @Mock
    int chmod(
      final String name,
      final int mode)
    {
      LOG.debug(
        "chmod: {} {}",
        name,
        Integer.valueOf(mode));

      final String ps;
      final Path actual = this.path.resolve(name);
      if (mode == 0755) {
        ps = "rwxr-xr-x";
      } else if (mode == 0644) {
        ps = "rw-r--r--";
      } else {
        ps = "---------";
      }

      try {
        Files.setPosixFilePermissions(
          actual, PosixFilePermissions.fromString(ps));
      } catch (final IOException e) {
        LOG.error("failed to set file permissions: ", e);
        return -1;
      }

      return 0;
    }

    @Mock
    int lchmod(
      final String name,
      final int mode)
    {
      LOG.debug(
        "lchmod: {} {}",
        name,
        Integer.valueOf(mode));
      return 0;
    }

  }
}
