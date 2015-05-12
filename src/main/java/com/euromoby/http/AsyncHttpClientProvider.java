package com.euromoby.http;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpHost;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.euromoby.agent.AgentManager;
import com.euromoby.agent.Config;
import com.euromoby.model.AgentId;
import com.euromoby.rest.RestServer;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;

@Component
public class AsyncHttpClientProvider {

	private static final Logger log = LoggerFactory.getLogger(AsyncHttpClientProvider.class);
	
	public static final String HTTPS = "https";

	public static final Pattern WILDCARD_REGEX = Pattern.compile("[^*]+|(\\*)");	
	
	private Config config;
	private AgentManager agentManager;
	private SSLContextProvider sslContextProvider;

	@Autowired
	public AsyncHttpClientProvider(Config config, AgentManager agentManager, SSLContextProvider sslContextProvider) {
		this.config = config;
		this.agentManager = agentManager;
		this.sslContextProvider = sslContextProvider;
	}

	public AsyncHttpClient createAsyncHttpClient() {
		
		AsyncHttpClientConfig.Builder configBuilder  = new AsyncHttpClientConfig.Builder();
		int timeout = config.getHttpClientTimeout();
		configBuilder.setConnectTimeout(timeout);
		configBuilder.setReadTimeout(timeout);
		configBuilder.setFollowRedirect(true);
		
		return new AsyncHttpClient(configBuilder.build());
		
//		return HttpClientBuilder.create()
//				.setSslcontext(sslContextProvider.getSSLContext())
//				.setSSLHostnameVerifier(new NoopHostnameVerifier())
//				.build();
	}

	public HttpClientContext createHttpClientContext() {
		HttpClientContext context = HttpClientContext.create();
		
		Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
	            .register("basic", new BasicSchemeFactory()).build();
	    context.setAuthSchemeRegistry(authSchemeRegistry);

	    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        context.setCredentialsProvider(credentialsProvider);	

	    AuthCache authCache = new BasicAuthCache();
        context.setAuthCache(authCache);

	    BasicScheme basicAuth = new BasicScheme();
	    UsernamePasswordCredentials userPasswordCredential = new UsernamePasswordCredentials(config.getRestLogin(), config.getRestPassword());
	    
	    List<AgentId> agents = agentManager.getAll();
	    for (AgentId agentId : agents) {
	    	int restPort = agentId.getBasePort() + RestServer.REST_PORT; 
		    credentialsProvider.setCredentials(new AuthScope(agentId.getHost(), restPort), userPasswordCredential);	    	
		    authCache.put(new HttpHost(agentId.getHost(), restPort, HTTPS), basicAuth);
	    }
        
	    // proxy authentication
	    if (config.isHttpProxy() && config.isHttpProxyAuthentication()) {
		    UsernamePasswordCredentials proxyCredentials = new UsernamePasswordCredentials(config.getHttpProxyLogin(), config.getHttpProxyPassword());	    	
		    credentialsProvider.setCredentials(new AuthScope(config.getHttpProxyHost(), config.getHttpProxyPort()), proxyCredentials);
		    BasicScheme proxyAuth = new BasicScheme();
		    try {
		    	proxyAuth.processChallenge( new BasicHeader( AUTH.PROXY_AUTH, "BASIC preemptive" ) );
		    } catch (Exception e) {
		    	log.warn("Proxy authentication failed", e);
		    }
		    authCache.put(new HttpHost(config.getHttpProxyHost(), config.getHttpProxyPort()), proxyAuth);
	    }
	    
        return context;
	}
	
	public RequestConfig.Builder createRequestConfigBuilder(String host, boolean noProxy) {
		
		int timeout = config.getHttpClientTimeout();
		
		RequestConfig.Builder requestConfigBuilder = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout);
		if (!noProxy && config.isHttpProxy() && !bypassProxy(host)) {
			requestConfigBuilder.setProxy(new HttpHost(config.getHttpProxyHost(), config.getHttpProxyPort()));
			requestConfigBuilder.setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC));
		}
		return requestConfigBuilder;
	}

	protected boolean bypassProxy(String host) {
		String[] proxyBypass = config.getHttpProxyBypass();
		
		for (String mask : proxyBypass) {
			Matcher m = WILDCARD_REGEX.matcher(mask);
			StringBuffer b = new StringBuffer();
			while (m.find()) {
				if (m.group(1) != null) {
					m.appendReplacement(b, ".*");
				} else {
					m.appendReplacement(b, "\\\\Q" + m.group(0) + "\\\\E");
				}
			}
			m.appendTail(b);
			
			if (host.matches(b.toString())) {
				return true;
			}
		}
		
		return false;
	}
	
}