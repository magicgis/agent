package com.euromoby.cdn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.euromoby.agent.AgentManager;
import com.euromoby.agent.Config;
import com.euromoby.http.HttpClientProvider;
import com.euromoby.model.AgentId;
import com.euromoby.rest.handler.fileinfo.FileInfo;
import com.google.gson.Gson;

@RunWith(MockitoJUnitRunner.class)
public class CdnNetworkTest {

	private static final Gson GSON = new Gson();
	private static final AgentId AGENT1 = new AgentId("agent1:21000");
	private static final AgentId AGENT2 = new AgentId("agent2:21000");
	
	@Mock
	Config config;
	@Mock
	AgentManager agentManager;
	@Mock
	HttpClientProvider httpClientProvider;
	@Mock
	CdnResourceMapping cdnResourceMapping;
	@Mock
	CdnResource cdnResource;
	@Mock
	CloseableHttpClient httpClient;
	@Mock 
	RequestConfig.Builder requestConfigBuilder;
	@Mock
	CloseableHttpResponse response;

	CdnNetwork cdnNetwork;

	private static final String GOOD_URL = "/good";
	private static final String BAD_URL = "/bad";	
	
	@Before
	public void init() {
		Mockito.when(config.getCdnPoolSize()).thenReturn(2);
		Mockito.when(cdnResourceMapping.findByUrl(Matchers.eq(GOOD_URL))).thenReturn(cdnResource);
		Mockito.when(cdnResourceMapping.findByUrl(Matchers.eq(BAD_URL))).thenReturn(null);		
		cdnNetwork = new CdnNetwork(config, agentManager, httpClientProvider, cdnResourceMapping);
	}
	
	@Test
	public void testUrlIsAvailable() {
		assertTrue(cdnNetwork.isAvailable(GOOD_URL));
	}

	@Test
	public void testUrlIsNotAvailable() {
		assertFalse(cdnNetwork.isAvailable(BAD_URL));
	}
	
	@Test
	public void testSendAndReceiveNotFound() throws Exception {
		List<AgentId> agentList = Collections.singletonList(AGENT1);
		Mockito.when(agentManager.getActive()).thenReturn(agentList);
		Mockito.when(httpClientProvider.createHttpClient()).thenReturn(httpClient);
		Mockito.when(httpClientProvider.createRequestConfigBuilder(Matchers.eq(false))).thenReturn(requestConfigBuilder);
		Mockito.when(httpClient.execute(Matchers.any(HttpUriRequest.class), Matchers.any(HttpClientContext.class))).thenReturn(response);
		Mockito.when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND, "Not found"));
		int agentCount = cdnNetwork.sendRequestsToActiveAgents(GOOD_URL);
		assertEquals(agentList.size(), agentCount);
		Mockito.when(config.getCdnTimeout()).thenReturn(500);
		List<FileInfo> fileInfoList = cdnNetwork.getResponsesFromAgents(agentCount);
		// 404 not found = null
		assertTrue(fileInfoList.isEmpty());
	}
	
	@Test
	public void testSendAndReceiveFound() throws Exception {
		List<AgentId> agentList = Arrays.asList(AGENT1, AGENT2);
		Mockito.when(agentManager.getActive()).thenReturn(agentList);
		Mockito.when(httpClientProvider.createHttpClient()).thenReturn(httpClient);
		Mockito.when(httpClientProvider.createRequestConfigBuilder(Matchers.eq(false))).thenReturn(requestConfigBuilder);
		Mockito.when(httpClient.execute(Matchers.any(HttpUriRequest.class), Matchers.any(HttpClientContext.class))).thenReturn(response);
		Mockito.when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "Found"));
		FileInfo fileInfo = new FileInfo();
		fileInfo.setAgentId(AGENT1);		
		ByteArrayEntity bae = new ByteArrayEntity(GSON.toJson(fileInfo).getBytes());
		Mockito.when(response.getEntity()).thenReturn(bae);

		int agentCount = cdnNetwork.sendRequestsToActiveAgents(GOOD_URL);
		assertEquals(agentList.size(), agentCount);
		Mockito.when(config.getCdnTimeout()).thenReturn(500);


		List<FileInfo> fileInfoList = cdnNetwork.getResponsesFromAgents(agentCount);
		// should be found
		assertEquals(agentList.size(), fileInfoList.size());
	}	
	
}