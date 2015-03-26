package utils;

import io.netty.util.CharsetUtil;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import agent.Configuration;

public class HttpUtils {

	public static RequestConfig.Builder createRequestConfigBuilder(Configuration config, boolean noProxy) {
		RequestConfig.Builder requestConfigBuilder = RequestConfig.custom().setSocketTimeout(3000).setConnectTimeout(3000);
		if (!noProxy && config.isHttpProxy()) {
			requestConfigBuilder.setProxy(new HttpHost(config.getHttpProxyHost(), config.getHttpProxyPort()));
		}
		return requestConfigBuilder;
	}

	public static String getContentFromUrl(Configuration config, String url, boolean noProxy) throws Exception {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		try {
			RequestConfig.Builder requestConfigBuilder = HttpUtils.createRequestConfigBuilder(config, noProxy);

			HttpGet request = new HttpGet(url);
			request.setConfig(requestConfigBuilder.build());

			CloseableHttpResponse response = httpclient.execute(request);

			try {
				StatusLine statusLine = response.getStatusLine();
				if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
					EntityUtils.consumeQuietly(response.getEntity());
					throw new Exception(statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
				}

				HttpEntity entity = response.getEntity();
				if (entity != null) {
					return EntityUtils.toString(entity, CharsetUtil.UTF_8);
				}
				return null;
			} finally {
				response.close();
			}
		} finally {
			httpclient.close();
		}
	}

}
