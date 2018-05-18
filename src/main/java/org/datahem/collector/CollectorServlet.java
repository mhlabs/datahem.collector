package org.datahem.collector;

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


import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;

import com.google.apphosting.api.ApiProxy;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;

import com.google.auth.oauth2.GoogleCredentials;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.protobuf.Timestamp;

import org.datahem.protobuf.collector.v1.CollectorPayloadEntityProto.*;
import org.datahem.collector.utils.PubSubHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

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


/**
 * Publishes messages to the application topic.
 */
// [START example]
@SuppressWarnings("serial")
@WebServlet(
	name = "collector",
	description = "Collect hits and put on pubsub")
public class CollectorServlet extends HttpServlet {
	private static final List<String> HEADERS = Stream.of("X-AppEngine-Country","X-AppEngine-Region","X-AppEngine-City","X-AppEngine-CityLatLong","user-agent").collect(Collectors.toList());
	private static final Logger LOG = LoggerFactory.getLogger(CollectorServlet.class);
	
    @Override
    public final void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        String pubSubTopicId = getServletConfig().getInitParameter("pubSubTopicId");
        String pubSubProjectId = getServletConfig().getInitParameter("pubSubProjectId");
        
        BufferedReader reader = req.getReader();     
        String payload = null;
        //Read and emit multiple rows of payload sent in the same POST
        while ((payload = reader.readLine()) != null) {
        	if (!"".equals(payload)) {
        		buildCollectorPayload(payload, req, pubSubProjectId, pubSubTopicId);
        	}    
        }
        
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        resp.getWriter().close();
    }
    
    @Override
    public final void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        String pubSubTopicId = getServletConfig().getInitParameter("pubSubTopicId");
        String pubSubProjectId = getServletConfig().getInitParameter("pubSubProjectId");
        
        String payload = req.getQueryString();        
        //Check if post body contains payload and create a proto
        if (!"".equals(payload)) {
        	buildCollectorPayload(payload, req, pubSubProjectId, pubSubTopicId);
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

	private static void buildCollectorPayload(String payload, HttpServletRequest req, String pubSubProjectId, String pubSubTopicId) throws IOException{
		List<CollectorPayloadEntity> collectorPayloadEntities = new ArrayList<>();
        long timestampMillis = Instant.now().getMillis();
        
        //If no pubSubProjectId in servlet config, use application id (first remove region prefix, i.e. s~ or e~)
        String appId = ApiProxy.getCurrentEnvironment().getAppId().replaceFirst("^[a-zA-Z]~", "");
        pubSubProjectId = (pubSubProjectId==null) ? appId : pubSubProjectId;
        
        String uuid = UUID.randomUUID().toString();
		
		Enumeration<String> headerNames = req.getHeaderNames();
        
        Map<String, String> headers = Collections
	        .list(headerNames)
	        .stream()
	      	.filter(s -> HEADERS.contains(s)) //Do we want to filter out sensitive fields and only keep those specified in HEADERS
			.map(s -> new String[]{s, req.getHeader(s)})
        	.collect(Collectors.toMap(s -> s[0], s -> s[1]));
        
        LOG.info(Arrays.toString(headers.entrySet().toArray()));
        
      	CollectorPayloadEntity collectorPayloadEntity = CollectorPayloadEntity.newBuilder()
			.setPayload(payload)
			.putAllHeaders(headers)
			.setEpochMillis(Long.toString(timestampMillis))
			.setUuid(uuid)
			.build();
            
      	collectorPayloadEntities.add(collectorPayloadEntity);
       	PubSubHelper.publishMessages(collectorPayloadEntities, pubSubProjectId, pubSubTopicId);
	}
}
