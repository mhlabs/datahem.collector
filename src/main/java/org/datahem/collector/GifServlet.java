package org.datahem.collector.measurementprotocol;

/*-
 * ========================LICENSE_START=================================
 * DataHem
 * %%
 * Copyright (C) 2018 Robert Sahlin and MatHem Sverige AB
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * =========================LICENSE_END==================================
 */

/*
import com.google.api.server.spi.auth.EspAuthenticator;
import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.AnnotationBoolean;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiIssuer;
import com.google.api.server.spi.config.ApiIssuerAudience;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.response.UnauthorizedException;
*/
import com.google.apphosting.api.ApiProxy;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.ProjectTopicName;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.net.URLEncoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.stream.Stream;
import java.util.Enumeration;
import java.util.UUID;

import org.joda.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
@WebServlet(
	name = "gifservlet",
	description = "Collect get-hits and put on pubsub")
public class GifServlet extends HttpServlet {
	private static final Logger LOG = LoggerFactory.getLogger(GifServlet.class);
	private static final List<String> HEADERS = Stream.of("X-AppEngine-Country","X-AppEngine-Region","X-AppEngine-City","X-AppEngine-CityLatLong","User-Agent").collect(Collectors.toList());

    // [START collect_get_gif]

    public final void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		String payload = req.getQueryString();
        String stream = req.getParameter("cstream");
		if (!"".equals(payload)) {
			buildCollectorPayload(payload, req, stream);
		}
        byte[] trackingGif = { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x1, 0x0, 0x1, 0x0, (byte) 0x80, 0x0, 0x0,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0, 0x0, 0x0, 0x2c, 0x0, 0x0, 0x0, 0x0, 0x1, 0x0, 0x1, 0x0,
            0x0, 0x2, 0x2, 0x44, 0x1, 0x0, 0x3b };

    	resp.setContentType("image/gif");
    	resp.setContentLength(trackingGif.length);

    	OutputStream out = resp.getOutputStream();
    	out.write(trackingGif);
    	out.close();
    }
    // [END collect_get_gif]



private static String encode(Object decoded) {
		try {
			return decoded == null ? "" : URLEncoder.encode(String.valueOf(decoded), "UTF-8").replace("+", "%20");
		} catch(final UnsupportedEncodingException e) {
			throw new RuntimeException("Impossible: UTF-8 is a required encoding", e);
		}
	}

private void buildCollectorPayload(String payload, HttpServletRequest req, String stream) throws IOException{
		long timestampMillis = Instant.now().getMillis();
		String uuid = UUID.randomUUID().toString();
        
		//Use application id to get project id (first remove region prefix, i.e. s~ or e~)
		String pubSubProjectId = ApiProxy.getCurrentEnvironment().getAppId().replaceFirst("^[a-zA-Z]~", "");
		Enumeration<String> headerNames = req.getHeaderNames();

        Map<String, String> headers = Collections
			.list(headerNames)
			.stream()
			.filter(s -> HEADERS.contains(s)) //Filter out sensitive fields and only keep those specified in HEADERS
			.map(s -> new String[]{s, req.getHeader(s)})
			.collect(Collectors.toMap(s -> s[0], s -> s[1]));

		PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
			.putAllAttributes(
			ImmutableMap.<String, String>builder()
                .putAll(headers)
				.put("MessageTimestamp", Long.toString(timestampMillis))
				.put("MessageStream", stream)
				.put("MessageUuid", uuid)
				.build()
			)
			.setData(ByteString.copyFromUtf8(payload))
			.build();

		publishMessage(pubsubMessage, pubSubProjectId, stream);
	}
	
	private void publishMessage(PubsubMessage pubsubMessage, String pubSubProjectId, String pubSubTopicId) throws IOException{
		Publisher publisher = PubSubClient.getPublisher(pubSubTopicId);
		try {
			publisher.publish(pubsubMessage);
		}
		catch(Exception e){
			LOG.error("uuid: " + pubsubMessage.getAttributes().get("MessageUuid") + ", Message: " + pubsubMessage.toString() + " Exception: " + e.getMessage());
		}
	}
}