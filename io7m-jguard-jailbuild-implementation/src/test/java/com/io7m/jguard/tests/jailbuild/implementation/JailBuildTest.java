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

import com.io7m.jguard.jailbuild.api.JailBuildType;
import com.io7m.jguard.jailbuild.api.JailDownloadOctetsPerSecond;
import com.io7m.jguard.jailbuild.api.JailDownloadProgressType;
import com.io7m.jguard.jailbuild.implementation.JailBuild;
import mockit.Mock;
import mockit.MockUp;
import mockit.StrictExpectations;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class JailBuildTest
{
  private static final JailDownloadProgressType PROGRESS;
  private static final URI BASE_URI;

  static {
    BASE_URI =
      URI.create("http://ftp.freebsd.org/pub/FreeBSD/releases/");

    PROGRESS =
      JailDownloadOctetsPerSecond.get(
        (total_expected, total_received, octets_per_second) -> {
        },
        Clock.systemUTC());
  }

  @Rule public ExpectedException expected = ExpectedException.none();

  private ExecutorService pool;
  private FileSystem filesystem;

  @Before
  public void onSetup()
    throws Exception
  {
    this.pool = Executors.newSingleThreadExecutor();
    this.filesystem = TestFilesystems.makeEmptyUnixFilesystem();
  }

  @After
  public void onTearDown()
    throws Exception
  {
    this.pool.shutdown();
    this.pool.awaitTermination(5L, TimeUnit.SECONDS);
    this.filesystem.close();
  }

  @Test
  public <T extends CloseableHttpResponse> void testDownloadSyncBadURI()
    throws Exception
  {
    final CloseableHttpClient mock_http_client =
      new MockUp<CloseableHttpClient>()
      {
      }.getMockInstance();

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

    final Path file =
      this.filesystem.getPath("/base.txz");

    this.expected.expect(IllegalArgumentException.class);
    this.expected.expectCause(Is.isA(URISyntaxException.class));

    build.jailDownloadBinaryArchiveSync(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "file with spaces",
      Optional.of(PROGRESS));
  }

  @Test
  public <T extends CloseableHttpResponse> void testDownloadSyncHead404()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

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

    build.jailDownloadBinaryArchiveSync(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public <T extends CloseableHttpResponse> void testDownloadSyncHeadGibberish0()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

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

    build.jailDownloadBinaryArchiveSync(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public <T extends CloseableHttpResponse> void testDownloadSyncHeadGibberish1()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

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

    build.jailDownloadBinaryArchiveSync(
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
  public <T extends CloseableHttpResponse> void testDownloadGet404()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

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

    build.jailDownloadBinaryArchiveSync(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public <T extends CloseableHttpResponse> void testDownloadGetBadContentLength()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith("Server failed to return a usable HTTP entity."));

    build.jailDownloadBinaryArchiveSync(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public <T extends CloseableHttpResponse> void testDownloadGetShortData()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

    final Path file =
      this.filesystem.getPath("/base.txz");

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    this.expected.expect(IOException.class);
    this.expected.expectMessage(
      new StringStartsWith("Downloaded file was truncated."));

    build.jailDownloadBinaryArchiveSync(
      file,
      BASE_URI,
      "amd64",
      "10.3-RELEASE",
      "base.txz",
      Optional.of(PROGRESS));
  }

  @Test
  public <T extends CloseableHttpResponse> void testDownloadGetAllCorrect()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    final Path file = this.filesystem.getPath("/base.txz");
    Assert.assertFalse(Files.exists(file));

    build.jailDownloadBinaryArchiveSync(
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
  public <T extends CloseableHttpResponse> void testDownloadGetResume()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    build.jailDownloadBinaryArchiveSync(
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
  public <T extends CloseableHttpResponse> void testDownloadGetGotAlready()
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

    final JailBuildType build =
      JailBuild.get(() -> mock_http_client, this.pool);

    new StrictExpectations()
    {{
      mock_http_client.close();
    }};

    Assert.assertEquals(32L, Files.size(file));

    build.jailDownloadBinaryArchiveSync(
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
}
