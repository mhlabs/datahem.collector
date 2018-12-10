/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import com.google.pubsub.v1.PubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpServletRequest;

import org.datahem.protobuf.collector.v1.CollectorPayloadEntityProto.*;
import org.datahem.collector.utils.PubSubHelper;

import java.io.BufferedReader;
import java.io.IOException;

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

import com.google.apphosting.api.ApiProxy;
import org.joda.time.Instant;

//import java.net.URL;
//import java.net.URLDecoder;
import java.net.URLEncoder;
//import java.util.Arrays;
//import java.util.LinkedHashMap;
//import java.util.regex.Pattern;
//import java.util.stream.Collectors;
//import java.util.AbstractMap.SimpleImmutableEntry;
//import java.util.*;
import java.io.UnsupportedEncodingException;
//import java.net.MalformedURLException;
import com.google.protobuf.ByteString;
import com.google.common.collect.ImmutableMap;

/**
 * The Collect API which Endpoints will be exposing.
 */
// [START collect_api_annotation]
@Api(
    name = "measurementprotocol",
    version = "v1",
    namespace =
    @ApiNamespace(
        ownerDomain = "datahem.org",
        ownerName = "datahem.org",
        packagePath = ""
    )
)
// [END echo_api_annotation]

public class Collect {
	private static final Logger LOG = LoggerFactory.getLogger(Collect.class);
	private static final List<String> HEADERS = Stream.of("X-AppEngine-Country","X-AppEngine-Region","X-AppEngine-City","X-AppEngine-CityLatLong","User-Agent").collect(Collectors.toList());

  /**
   * Collects data, converts it into protobuf and publish on pubSub. Returns 204 on success.
   */
  // [START collect_post]
	@ApiMethod(name = "post", path = "collect/{stream}", httpMethod = ApiMethod.HttpMethod.POST)
	public void collect_post(HttpServletRequest req, Payload payload, @Named("stream") String stream) throws IOException {
		//LOG.info("payload: " + payload.getPayload());
		buildCollectorPayload(payload.getPayload(), req, stream);
	}
// [END collect_post]

// [START echo_method]
	@ApiMethod(name = "get", path = "collect/{stream}", httpMethod = ApiMethod.HttpMethod.GET)
	public void collect_get(HttpServletRequest req, @Named("stream") String stream) throws IOException{
		String payload = req.getQueryString();
		if (!"".equals(payload)) {
			//LOG.info("payload: " + payload);
			buildCollectorPayload(payload, req, stream);
		}
}
// [END echo_method]

private static String encode(Object decoded) {
    	try {
        	return decoded == null ? "" : URLEncoder.encode(String.valueOf(decoded), "UTF-8").replace("+", "%20");
    	} catch(final UnsupportedEncodingException e) {
        	throw new RuntimeException("Impossible: UTF-8 is a required encoding", e);
    	}
	}

private static void buildCollectorPayload(String payload, HttpServletRequest req, String stream) throws IOException{
		//List<CollectorPayloadEntity> collectorPayloadEntities = new ArrayList<>();
		long timestampMillis = Instant.now().getMillis();

		//Use application id to get project id (first remove region prefix, i.e. s~ or e~)
		String pubSubProjectId = ApiProxy.getCurrentEnvironment().getAppId().replaceFirst("^[a-zA-Z]~", "");
		String uuid = UUID.randomUUID().toString();
		
		Enumeration<String> headerNames = req.getHeaderNames();

		String headers = Collections
			.list(headerNames)
			.stream()
			.filter(s -> HEADERS.contains(s)) //Filter out sensitive fields and only keep those specified in HEADERS
			.map(s -> s + "=" + req.getHeader(s))
			.collect(Collectors.joining("&"));
			
			//LOG.info(payload + "&" + encode(headersPayload));
			payload += "&" + encode(headers);

			PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
				.putAllAttributes(
				ImmutableMap.<String, String>builder()
					.put("timestamp", Long.toString(timestampMillis))
					.put("stream", stream)
					.put("uuid", uuid)
					.build() 
				)
				//.setData(payload.toByteString())
				.setData(ByteString.copyFromUtf8(payload))
				.build();

			PubSubHelper.publishMessage(pubsubMessage, pubSubProjectId, stream);

	}
}
