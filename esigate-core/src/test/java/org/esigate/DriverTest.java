/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.esigate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.esigate.cookie.CookieManager;
import org.esigate.esi.EsiRenderer;
import org.esigate.events.EventManager;
import org.esigate.extension.ExtensionFactory;
import org.esigate.http.DateUtils;
import org.esigate.http.HttpClientHelper;
import org.esigate.http.MockHttpClient;
import org.esigate.tags.BlockRenderer;
import org.esigate.tags.TemplateRenderer;
import org.esigate.test.TestUtils;
import org.esigate.util.HttpRequestHelper;

public class DriverTest extends TestCase {
	private HttpEntityEnclosingRequest request;

	@Override
	protected void setUp() throws Exception {
		MockDriver provider = new MockDriver("mock");
		provider.addResource("/testBlock", "abc some<!--$beginblock$A$-->some text goes here<!--$endblock$A$--> cdf hello");
		provider.addResource("/testTemplateFullPage", "some <!--$beginparam$key$-->some hidden text goes here<!--$endparam$key$--> printed");
		provider.addResource("/testTemplate", "abc some<!--$begintemplate$A$-->some text goes here<!--$endtemplate$A$--> cdf hello");
		request = TestUtils.createRequest();
	}

	public void testRenderBlock() throws IOException, HttpErrorPage {
		Writer out = new StringWriter();
		DriverFactory.getInstance("mock").render("/testBlock", null, out, request, new BlockRenderer("A", "/testBlock"));

		assertEquals("some text goes here", out.toString());

		out = new StringWriter();
		DriverFactory.getInstance("mock").render("$(vartestBlock)", null, out, request, new BlockRenderer("A", "$(vartestBlock)"));
		assertEquals("some text goes here", out.toString());

		out = new StringWriter();
		DriverFactory.getInstance("mock").render("/$(vartest)$(varBlock)", null, out, request, new BlockRenderer("A", "/$(vartest)$(varBlock)"));
		assertEquals("some text goes here", out.toString());

	}

	public void testRenderTemplateFullPage() throws IOException, HttpErrorPage {
		StringWriter out = new StringWriter();
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("key", "'value'");
		params.put("some other key", "'another value'");
		DriverFactory.getInstance("mock").render("/testTemplateFullPage", null, out, request, new TemplateRenderer(null, params, "/testTemplateFullPage"));
		assertFalse(out.toString().contains("key"));
		assertTrue(out.toString().contains("'value'"));
		assertFalse(out.toString().contains("some other key"));
		assertEquals("some 'value' printed", out.toString());
	}

	public void testRenderTemplate() throws IOException, HttpErrorPage {
		StringWriter out = new StringWriter();
		DriverFactory.getInstance("mock").render("/testTemplate", null, out, request, new TemplateRenderer("A", null, "/testTemplate"));
		assertEquals("some text goes here", out.toString());

		out = new StringWriter();
		DriverFactory.getInstance("mock").render("/test$(varTemplate)", null, out, request, new TemplateRenderer("A", null, "/test$(varTemplate)"));
		assertEquals("some text goes here", out.toString());

	}

	/** Test default configuration */
	public void testDefaultConfig() {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		Driver driver = new Driver("dummy", properties);
		// Parsable contentTypes
		assertParsableContentType(driver, "text/html; charset=utf-8");
		assertParsableContentType(driver, "application/xhtml+xml; charset=iso-8859-1");
		assertNotParsableContentType(driver, "application/octet-stream");
	}

	private void assertParsableContentType(Driver driver, String contentType) {
		assertTrue("Content-type should be considered as text", driver.isTextContentType(contentType));
	}

	private void assertNotParsableContentType(Driver driver, String contentType) {
		assertFalse("Content-type should be considered as binary", driver.isTextContentType(contentType));
	}

	/**
	 * Test property parsableContentTypes
	 */
	public void testParsableContentTypes() {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		properties.put(Parameters.PARSABLE_CONTENT_TYPES.name, "text/plain");
		Driver driver = new Driver("dummy", properties);
		assertParsableContentType(driver, "text/plain");

		properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		properties.put(Parameters.PARSABLE_CONTENT_TYPES.name, "text/plain, text/html");
		driver = new Driver("dummy", properties);
		assertParsableContentType(driver, "text/plain");
		assertParsableContentType(driver, "text/html");

		properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		properties.put(Parameters.PARSABLE_CONTENT_TYPES.name, "text/plain, text/html,application/x");
		driver = new Driver("dummy", properties);
		assertParsableContentType(driver, "text/plain");
		assertParsableContentType(driver, "text/html");
		assertParsableContentType(driver, "application/x");
	}

	public void testHeadersPreservedWhenError500() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 500, "Internal Server Error");
		response.addHeader("Content-type", "Text/html;Charset=UTF-8");
		response.addHeader("Dummy", "dummy");
		HttpEntity httpEntity = new StringEntity("Error", "UTF-8");
		response.setEntity(httpEntity);
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);
		try {
			driver.proxy("/", request);
			fail("We should get an HttpErrorPage");
		} catch (HttpErrorPage e) {
			TestUtils.sendHttpErrorPage(e, request);
		}
		assertEquals("Status code", 500, TestUtils.getResponse(request).getStatusLine().getStatusCode());
		assertTrue("Header 'Dummy'", TestUtils.getResponse(request).containsHeader("Dummy"));
	}

	public void testHeadersFilteredWhenError500() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 500, "Internal Server Error");
		response.addHeader("Content-type", "Text/html;Charset=UTF-8");
		response.addHeader("Transfer-Encoding", "dummy");
		HttpEntity httpEntity = new StringEntity("Error", "UTF-8");
		response.setEntity(httpEntity);
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);
		try {
			driver.proxy("/", request);
			fail("We should get an HttpErrorPage");
		} catch (HttpErrorPage e) {
			TestUtils.sendHttpErrorPage(e, request);
		}
		assertEquals("Status code", 500, TestUtils.getResponse(request).getStatusLine().getStatusCode());
		assertFalse("Header 'Transfer-Encoding'", TestUtils.getResponse(request).containsHeader("Transfer-Encoding"));
	}

	public void testSpecialCharacterInErrorPage() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 500, "Internal Server Error");
		response.addHeader("Content-type", "Text/html;Charset=UTF-8");
		HttpEntity httpEntity = new StringEntity("é", "UTF-8");
		response.setEntity(httpEntity);
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);
		try {
			driver.proxy("/", request);
			fail("We should get an HttpErrorPage");
		} catch (HttpErrorPage e) {
			TestUtils.sendHttpErrorPage(e, request);
		}
		assertEquals("é", TestUtils.getResponseBodyAsString(request));
	}

	public void testGzipErrorPage() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 500, "Internal Server Error");
		response.addHeader("Content-type", "Text/html;Charset=UTF-8");
		response.addHeader("Content-encoding", "gzip");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gzos = new GZIPOutputStream(baos);
		byte[] uncompressedBytes = "é".getBytes("UTF-8");
		gzos.write(uncompressedBytes, 0, uncompressedBytes.length);
		gzos.close();
		byte[] compressedBytes = baos.toByteArray();
		ByteArrayEntity httpEntity = new ByteArrayEntity(compressedBytes);
		httpEntity.setContentType("Text/html;Charset=UTF-8");
		httpEntity.setContentEncoding("gzip");
		response.setEntity(httpEntity);
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);
		try {
			driver.proxy("/", request);
			fail("We should get an HttpErrorPage");
		} catch (HttpErrorPage e) {
			TestUtils.sendHttpErrorPage(e, request);
		}
		assertEquals("é", TestUtils.getResponseBodyAsString(request));
	}

	private Driver createMockDriver(Properties properties, HttpClient httpClient) {
		return createMockDriver(properties, httpClient, "tested");
	}

	private Driver createMockDriver(Properties properties, HttpClient httpClient, String name) {
		CookieManager cookieManager = ExtensionFactory.getExtension(properties, Parameters.COOKIE_MANAGER, null);

		HttpClientHelper httpClientHelper = new HttpClientHelper(new EventManager(), cookieManager, httpClient, properties);
		Driver driver = new Driver(name, properties, httpClientHelper);
		DriverFactory.put(name, driver);
		return driver;
	}

	public void testRewriteRedirectResponse() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://www.foo.com:8080/");
		request = TestUtils.createRequest("http://www.bar.com/foo/");
		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_MOVED_TEMPORARILY, "Found");
		response.addHeader("Location", "http://www.foo.com:8080/somewhere/");
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);
		driver.proxy("/foo/", request);
		assertEquals("http://www.bar.com/somewhere/", TestUtils.getResponse(request).getFirstHeader("Location").getValue());
	}

	/**
	 * <p>
	 * Test bug Bug 142 (1st case)
	 * <p>
	 * 0000142: Error with 304 backend responses
	 * <p>
	 * NPE in Apache HTTP CLient cache
	 * <p>
	 * See
	 * :https://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=142
	 * 
	 * @throws Exception
	 */
	public void testBug142() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://www.foo.com/");
		properties.put(Parameters.TTL.name, "43200");
		properties.put(Parameters.PRESERVE_HOST.name, "true");
		properties.put(Parameters.USE_CACHE.name, true);

		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_MODIFIED, "Not Modified");
		response.addHeader("Etag", "b5e3f57c0e84fc7a197b849fdfd3d407");
		response.addHeader("Date", "Thu, 13 Dec 2012 07:28:01 GMT");
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);

		// First request
		request = TestUtils.createRequest("http://www.bar.com/foo/");
		request.addHeader("If-None-Match", "b5e3f57c0e84fc7a197b849fdfd3d407");
		request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml");
		request.addHeader("If-Modified-Since", "Fri, 15 Jun 2012 21:06:25 GMT");
		request.addHeader("Cache-Control", "max-age=0");
		driver.proxy("/foo/", request);
		assertEquals(304, TestUtils.getResponse(request).getStatusLine().getStatusCode());

		// Second request
		request = TestUtils.createRequest("http://www.bar.com/foo/");
		request.addHeader("If-None-Match", "b5e3f57c0e84fc7a197b849fdfd3d407");
		request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml");
		request.addHeader("If-Modified-Since", "Fri, 15 Jun 2012 21:06:25 GMT");
		request.addHeader("Cache-Control", "max-age=0");

		driver.proxy("/foo/", request);
		assertEquals(304, TestUtils.getResponse(request).getStatusLine().getStatusCode());
	}

	/**
	 * <p>
	 * Test bug Bug 142 (2nd case)
	 * <p>
	 * 0000142: Error with 304 backend responses
	 * <p>
	 * NPE in Apache HTTP CLient cache
	 * <p>
	 * See https://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=142
	 * 
	 * @throws Exception
	 */
	public void testBug142_2() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://www.foo.com/");
		properties.put(Parameters.TTL.name, "43200");

		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_MODIFIED, "Not Modified");
		response.addHeader("Etag", "a86ecd6cc6d361776ed05f063921aa34");
		response.addHeader("Date", "Thu, 13 Dec 2012 08:55:37 GMT");
		response.addHeader("Cache-Control", "max-age=2051, public");
		response.addHeader("Expires", "Thu, 13 Dec 2012 09:29:48 GMT");
		response.addHeader("Vary", "Accept-Encoding");

		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);

		// First request
		request = TestUtils.createRequest("http://www.bar142-2.com/foobar142-2/");
		request.addHeader("If-None-Match", "a86ecd6cc6d361776ed05f063921aa34");
		driver.proxy("/foobar142-2/", request);
		assertEquals(304, TestUtils.getResponse(request).getStatusLine().getStatusCode());
	}

	/**
	 * <p>
	 * Test bug Bug 155
	 * <p>
	 * 0000155: 304 returned when not using If-XXX headers
	 * <p>
	 * When requesting a full response, a full response must be sent even if the
	 * cache has cached a 304 response.
	 * <p>
	 * See https://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=155
	 * 
	 * @throws Exception
	 */
	public void testBug155() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://www.foo.com/");
		properties.put(Parameters.TTL.name, "43200");
		properties.put(Parameters.PRESERVE_HOST.name, "true");
		properties.put(Parameters.USE_CACHE.name, true);

		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_MODIFIED, "Not Modified");
		response.addHeader("Etag", "a86ecd6cc6d361776ed05f063921aa34");
		response.addHeader("Date", "Thu, 13 Dec 2012 08:55:37 GMT");
		response.addHeader("Cache-Control", "max-age=2051, public, must-revalidate, proxy-revalidate");
		response.addHeader("Expires", "Thu, 13 Dec 2012 09:29:48 GMT");
		response.addHeader("Set-Cookie", "w3tc_referrer=http%3A%2F%2Fblog.richeton.com%2Fcategory%2Fcomputer%2Fwat%2F; path=/");
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);

		// First request
		request = TestUtils.createRequest("http://www.bar142-2.com/foobar142-2/");
		request.addHeader("If-None-Match", "a86ecd6cc6d361776ed05f063921aa34");
		request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml");
		request.addHeader("Cache-Control", "max-age=0");
		request.addHeader("Accept-Encoding", "gzip, deflate");
		driver.proxy("/foobar142-2/", request);
		assertEquals(304, TestUtils.getResponse(request).getStatusLine().getStatusCode());

		response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "Ok");
		response.addHeader("Etag", "a86ecd6cc6d361776ed05f063921aa34");
		response.addHeader("Date", "Thu, 13 Dec 2012 08:55:37 GMT");
		response.addHeader("Cache-Control", "max-age=2051, public, must-revalidate, proxy-revalidate");
		response.addHeader("Expires", "Thu, 13 Dec 2012 09:29:48 GMT");
		response.addHeader("Set-Cookie", "w3tc_referrer=http%3A%2F%2Fblog.richeton.com%2Fcategory%2Fcomputer%2Fwat%2F; path=/");
		response.setEntity(new StringEntity("test"));
		mockHttpClient.setResponse(response);

		// First request
		request = TestUtils.createRequest("http://www.bar142-2.com/foobar142-2/");
		request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml");
		request.addHeader("Cache-Control", "max-age=0");
		request.addHeader("Accept-Encoding", "gzip, deflate");
		driver.proxy("/foobar142-2/", request);
		assertEquals(200, TestUtils.getResponse(request).getStatusLine().getStatusCode());
		assertNotNull(TestUtils.getResponse(request).getEntity());
	}

	/**
	 * 0000162: Cookie forwarding (Browser->Server) does not work with
	 * preserveHost
	 * <p>
	 * This test is to ensure behavior with preserve host off (already working
	 * as of bug 162).
	 * 
	 * @throws Exception
	 * @see <a
	 *      href="http://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=162">0000162</a>
	 */
	public void testBug162_PreserveHostOff() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost.mydomain.fr/");
		properties.put(Parameters.PRESERVE_HOST.name, "false");
		properties.put(Parameters.FORWARD_COOKIES.name, "*");

		MockHttpClient mockHttpClient = new MockHttpClient();

		mockHttpClient.setHttpResponseExecutor(new HttpRequestExecutor() {
			@Override
			public HttpResponse execute(HttpRequest request, HttpClientConnection conn, HttpContext context) throws IOException, HttpException {
				Assert.assertTrue("Cookie must be forwarded", request.containsHeader("Cookie"));
				return new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK");
			}
		});
		Driver driver = createMockDriver(properties, mockHttpClient);

		request = TestUtils.createRequest("http://test.mydomain.fr/foobar/");
		TestUtils.addCookie(new BasicClientCookie("TEST_cookie", "233445436436346"), request);

		driver.proxy("/foobar/", request);

	}

	/**
	 * 0000162: Cookie forwarding (Browser->Server) does not work with
	 * preserveHost
	 * <p>
	 * This test ensure cookies are forwarded with preserveHost. Specific code
	 * is Cookie preparation in DefaultCookieManager#rewriteForServer().
	 * <p>
	 * Warning: HttpClient is not using Host header to validate cookies.
	 * 
	 * @throws Exception
	 * @see <a
	 *      href="http://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=162">0000162</a>
	 */
	public void testBug162_PreserveHostOn() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost.mydomain.fr/");
		properties.put(Parameters.PRESERVE_HOST.name, "true");
		properties.put(Parameters.FORWARD_COOKIES.name, "*");

		MockHttpClient mockHttpClient = new MockHttpClient();
		mockHttpClient.setHttpResponseExecutor(new HttpRequestExecutor() {
			@Override
			public HttpResponse execute(HttpRequest request, HttpClientConnection conn, HttpContext context) throws IOException, HttpException {
				Assert.assertTrue("Cookie must be forwarded", request.containsHeader("Cookie"));
				return new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK");
			}
		});
		Driver driver = createMockDriver(properties, mockHttpClient);

		request = TestUtils.createRequest("http://test.mydomain.fr/foobar/");
		request.addHeader("Host", "test.mydomain.fr");
		BasicClientCookie cookie = new BasicClientCookie("TEST_cookie", "233445436436346");
		TestUtils.addCookie(cookie, request);

		driver.proxy("/foobar/", request);

		// same test without forcing the host header
		request = TestUtils.createRequest("http://test.mydomain.fr/foobar/");
		TestUtils.addCookie(cookie, request);

	}

	/**
	 * 0000135: Special characters are lost when including a fragment with no
	 * charset specified into UTF-8 page
	 * 
	 * @throws Exception
	 * @see <a
	 *      href="http://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=135">0000135</a>
	 */
	public void testSpecialCharacterInIncludeNoCharset() throws Exception {
		String now = DateUtils.formatDate(new Date());

		// Create master application
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		MockHttpClient mockHttpClient = new MockHttpClient();
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "Ok");
		response.addHeader("Date", now);
		response.addHeader("Content-type", "Text/html;Charset=UTF-8");
		HttpEntity httpEntity = new StringEntity("à<esi:include src=\"$(PROVIDER{provider})/\"/>à", "UTF-8");
		response.setEntity(httpEntity);
		mockHttpClient.setResponse(response);
		Driver driver = createMockDriver(properties, mockHttpClient);

		// Create provider application
		properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost");
		mockHttpClient = new MockHttpClient();
		response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "Ok");
		response.addHeader("Date", now);
		response.addHeader("Content-type", "text/html");
		httpEntity = new ByteArrayEntity("é".getBytes("ISO-8859-1"), ContentType.create("text/html", (String) null));
		response.setEntity(httpEntity);
		mockHttpClient.setResponse(response);
		createMockDriver(properties, mockHttpClient, "provider");

		// Do the include and check the result
		driver.proxy("/", request, new EsiRenderer());
		assertEquals("àéà", TestUtils.getResponseBodyAsString(request));
	}

	public void testBug161_SetCookie() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE.name, "http://localhost/");
		properties.put(Parameters.PRESERVE_HOST.name, "true");
		properties.put(Parameters.FORWARD_COOKIES.name, "*");

		MockHttpClient mockHttpClient = new MockHttpClient();
		BasicHttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "Ok");
		response.addHeader("Date", "Thu, 13 Dec 2012 08:55:37 GMT");
		response.addHeader("Set-Cookie", "mycookie=123456; domain=mydomain.fr; path=/");
		response.setEntity(new StringEntity("test"));
		mockHttpClient.setResponse(response);

		Driver driver = createMockDriver(properties, mockHttpClient);

		request = TestUtils.createRequest("http://test.mydomain.fr/foobar/");

		driver.proxy("/foobar/", request);

		// https://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=161
		assertTrue("Set-Cookie must be forwarded.",
		HttpRequestHelper.getMediator(request).getCookies().length > 0);
	}

	/**
	 * 0000154: Warn on staleWhileRevalidate configuration issue
	 * https://sourceforge.net/apps/mantisbt/webassembletool/view.php?id=154
	 * 
	 * @throws Exception
	 */
	public void testConfigStaleWhileRevalidateWith0WorkerThreadsThrowsConfigurationException() throws Exception {
		Properties properties = new Properties();
		properties.put(Parameters.REMOTE_URL_BASE, "http://localhost/");
		properties.put(Parameters.STALE_WHILE_REVALIDATE, "600");

		MockHttpClient mockHttpClient = new MockHttpClient();
		try {
			createMockDriver(properties, mockHttpClient);
			fail("We should have had a ConfigurationException");
		} catch (ConfigurationException e) {
			// This is exactly what we want
		}
	}
}
