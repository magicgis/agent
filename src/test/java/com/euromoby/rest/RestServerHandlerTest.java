package com.euromoby.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.net.URI;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.euromoby.cdn.CdnNetwork;
import com.euromoby.file.MimeHelper;
import com.euromoby.rest.handler.RestHandler;

@RunWith(MockitoJUnitRunner.class)
public class RestServerHandlerTest {

	private static final String INVALID_URI = "$[level]/r$[y]_c$[x].jpg";
	private static final String UNKNOWN_URI = "http://example.com/unknown";
	private static final String GOOD_URI = "http://example.com/good";
	
	@Mock
	RestMapper restMapper;
	@Mock
	RestHandler restHandler;
	
	@Mock
	MimeHelper mimeHelper;
	@Mock
	CdnNetwork cdnNetwork;
	@Mock
	ChannelHandlerContext ctx;
	@Mock
	Channel channel;
	@Mock
	FullHttpRequest request;
	@Mock
	HttpContent httpContent;
	@Mock
	LastHttpContent lastHttpContent;
	@Mock
	HttpHeaders headers;
	@Mock
	ChannelFuture channelFuture;
	@Mock
	File targetFile;

	RestServerHandler handler;

	@Before
	public void init() throws Exception {
		Mockito.when(ctx.channel()).thenReturn(channel);
		Mockito.when(channel.writeAndFlush(Matchers.any(FullHttpResponse.class))).thenReturn(channelFuture);		
		Mockito.when(request.headers()).thenReturn(headers);
		Mockito.when(request.getProtocolVersion()).thenReturn(HttpVersion.HTTP_1_1);
		handler = new RestServerHandler(restMapper);
	}

	@Test
	public void testExceptionCaught() throws Exception {
		handler.exceptionCaught(ctx, new Exception());
		Mockito.verify(channel).close();
	}	

	@Test
	public void testSend100Continue() {
		handler.send100Continue(ctx);
		ArgumentCaptor<FullHttpResponse> captor = ArgumentCaptor.forClass(FullHttpResponse.class);
		Mockito.verify(ctx).write(captor.capture());
		FullHttpResponse response = captor.getValue();
		assertEquals(HttpResponseStatus.CONTINUE, response.getStatus());
	}	
	
	@Test
	public void testExecuteRestHandlerException() {
		try {
			handler.executeRestHandler(ctx);
			fail();
		} catch (IllegalStateException e) {}
	}

	@Test
	public void testExecuteRestHandlerOk() throws Exception {
		URI uri = new URI(GOOD_URI);
		Mockito.when(request.getUri()).thenReturn(GOOD_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(restHandler);
		Mockito.when(request.getMethod()).thenReturn(HttpMethod.GET);
		handler.setRequest(request);
		handler.processHttpRequest(ctx);
		assertNotNull(handler.getRestHandler());
		handler.executeRestHandler(ctx);
		Mockito.verify(restHandler).process(Matchers.eq(ctx), Matchers.eq(request), Matchers.any(HttpPostRequestDecoder.class));
	}	
	
	@Test
	public void testFindRestHandlerInvalidUri() {
		Mockito.when(request.getUri()).thenReturn(INVALID_URI);
		assertNull(handler.findRestHandler(request));
		Mockito.verifyZeroInteractions(restMapper);
	}

	@Test
	public void testFindRestHandlerUnknownUri() throws Exception {
		URI uri = new URI(UNKNOWN_URI);
		Mockito.when(request.getUri()).thenReturn(UNKNOWN_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(null);
		assertNull(handler.findRestHandler(request));
	}

	@Test
	public void testFindRestHandlerGoodUri() throws Exception {
		URI uri = new URI(GOOD_URI);
		Mockito.when(request.getUri()).thenReturn(GOOD_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(restHandler);
		assertEquals(restHandler, handler.findRestHandler(request));
	}	

	@Test
	public void testSendErrorResponse() {
		HttpResponseStatus status = HttpResponseStatus.BAD_REQUEST;
		RestException e = new RestException(status);
		ArgumentCaptor<FullHttpResponse> captor = ArgumentCaptor.forClass(FullHttpResponse.class);
		handler.sendErrorResponse(ctx, e);
		Mockito.verify(channel).writeAndFlush(captor.capture());
		Mockito.verify(channel).close();
		FullHttpResponse response = captor.getValue();
		assertEquals(status, response.getStatus());
		assertEquals(RestServerHandler.TEXT_PLAIN_UTF8, response.headers().get(HttpHeaders.Names.CONTENT_TYPE));
		Mockito.verify(channelFuture).addListener(Matchers.eq(ChannelFutureListener.CLOSE));
	}

	@Test
	public void testProcessHttpRequestUnknownUri() throws Exception {	
		URI uri = new URI(UNKNOWN_URI);
		Mockito.when(request.getUri()).thenReturn(UNKNOWN_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(null);
		handler.setRequest(request);
		handler.processHttpRequest(ctx);
		assertNull(handler.getRestHandler());
		assertNull(handler.getHttpPostRequestDecoder());		
	}
	
	@Test
	public void testProcessHttpRequestGet() throws Exception {
		URI uri = new URI(GOOD_URI);
		Mockito.when(request.getUri()).thenReturn(GOOD_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(restHandler);
		Mockito.when(request.getMethod()).thenReturn(HttpMethod.GET);
		Mockito.when(headers.get(Matchers.refEq(HttpHeaders.newEntity(HttpHeaders.Names.EXPECT)))).thenReturn(HttpHeaders.Values.CONTINUE);
		handler.setRequest(request);
		handler.processHttpRequest(ctx);
		
		ArgumentCaptor<FullHttpResponse> captor = ArgumentCaptor.forClass(FullHttpResponse.class);		
		Mockito.verify(ctx).write(captor.capture());
		FullHttpResponse response = captor.getValue();
		assertEquals(HttpResponseStatus.CONTINUE, response.getStatus());
		assertNotNull(handler.getRestHandler());		
		assertNull(handler.getHttpPostRequestDecoder());
	}

	@Test
	public void testProcessHttpRequestPost() throws Exception {
		URI uri = new URI(GOOD_URI);
		Mockito.when(request.getUri()).thenReturn(GOOD_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(restHandler);
		Mockito.when(request.getMethod()).thenReturn(HttpMethod.POST);
		Mockito.when(request.content()).thenReturn(Unpooled.copiedBuffer("foobar", CharsetUtil.UTF_8));
		handler.setRequest(request);
		handler.processHttpRequest(ctx);
		
		Mockito.verifyZeroInteractions(ctx);
		assertNotNull(handler.getRestHandler());
		assertNotNull(handler.getHttpPostRequestDecoder());
	}	


	@Test
	public void testChannelRead0GetWithContent() throws Exception {
		URI uri = new URI(GOOD_URI);
		Mockito.when(request.getUri()).thenReturn(GOOD_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(restHandler);
		Mockito.when(request.getMethod()).thenReturn(HttpMethod.GET);		
		handler.channelRead0(ctx, request);
		assertNotNull(handler.getRestHandler());		
		assertNull(handler.getHttpPostRequestDecoder());		
		
		// send content
		handler.channelRead0(ctx, httpContent);
		Mockito.verify(restHandler).process(Matchers.eq(ctx), Matchers.eq(request), Matchers.any(HttpPostRequestDecoder.class));		
	}	
	
	@Test
	public void testChannelRead0PostWithContent() throws Exception {
		URI uri = new URI(GOOD_URI);
		Mockito.when(request.getUri()).thenReturn(GOOD_URI);
		Mockito.when(restMapper.getHandler(Matchers.eq(uri))).thenReturn(restHandler);
		Mockito.when(request.getMethod()).thenReturn(HttpMethod.POST);	
		Mockito.when(request.content()).thenReturn(Unpooled.copiedBuffer("foobar", CharsetUtil.UTF_8));
		handler.channelRead0(ctx, request);
		assertNotNull(handler.getRestHandler());		
		assertNotNull(handler.getHttpPostRequestDecoder());		
		
		// send content
		Mockito.when(httpContent.content()).thenReturn(Unpooled.copiedBuffer("foobar", CharsetUtil.UTF_8));
		handler.channelRead0(ctx, httpContent);
		
		Mockito.when(lastHttpContent.content()).thenReturn(Unpooled.copiedBuffer("foobar", CharsetUtil.UTF_8));		
		handler.channelRead0(ctx, lastHttpContent);		
		
		Mockito.verify(restHandler).process(Matchers.eq(ctx), Matchers.eq(request), Matchers.any(HttpPostRequestDecoder.class));			
		
	}
	
}
