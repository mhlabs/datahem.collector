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

package org.datahem.echo;

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

/**
 * The Echo API which Endpoints will be exposing.
 */
// [START echo_api_annotation]
@Api(
    name = "echo",
    version = "v1",
    namespace =
    @ApiNamespace(
        ownerDomain = "echo.datahem.org",
        ownerName = "echo.datahem.org",
        packagePath = ""
    )
)
// [END echo_api_annotation]

public class Echo {
	private static final Logger LOG = LoggerFactory.getLogger(Echo.class);
	private static final List<String> HEADERS = Stream.of("X-AppEngine-Country","X-AppEngine-Region","X-AppEngine-City","X-AppEngine-CityLatLong","user-agent").collect(Collectors.toList());

  /**
   * Echoes the received message back. If n is a non-negative integer, the message is copied that
   * many times in the returned message.
   *
   * <p>Note that name is specified and will override the default name of "{class name}.{method
   * name}". For example, the default is "echo.echo".
   *
   * <p>Note that httpMethod is not specified. This will default to a reasonable HTTP method
   * depending on the API method name. In this case, the HTTP method will default to POST.
   */
  // [START echo_method]
	@ApiMethod(name = "post", path = "collect/{stream}", httpMethod = ApiMethod.HttpMethod.POST)
	public void collect_post(HttpServletRequest req, Message message, @Named("stream") String stream) throws IOException {
		String payload = message.getMessage();
			if (!"".equals(payload)) {
				buildCollectorPayload(payload, req, stream);
			}
	}
// [END echo_method]

// [START echo_method]
	@ApiMethod(name = "get", path = "collect/{stream}", httpMethod = ApiMethod.HttpMethod.GET)
	public void collect_get(HttpServletRequest req, @Named("stream") String stream) throws IOException{
		String payload = req.getQueryString();        
		//Check if post body contains payload and create a proto
		if (!"".equals(payload)) {
			LOG.info("payload: " + payload);
			buildCollectorPayload(payload, req, stream);
		}
}
// [END echo_method]

private static void buildCollectorPayload(String payload, HttpServletRequest req, String stream) throws IOException{
		List<CollectorPayloadEntity> collectorPayloadEntities = new ArrayList<>();
		long timestampMillis = Instant.now().getMillis();

		//Use application id to get project id (first remove region prefix, i.e. s~ or e~)
		String pubSubProjectId = ApiProxy.getCurrentEnvironment().getAppId().replaceFirst("^[a-zA-Z]~", "");
		String uuid = UUID.randomUUID().toString();
		
		Enumeration<String> headerNames = req.getHeaderNames();

		Map<String, String> headers = Collections
			.list(headerNames)
			.stream()
			.filter(s -> HEADERS.contains(s)) //Filter out sensitive fields and only keep those specified in HEADERS
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
		PubSubHelper.publishMessages(collectorPayloadEntities, pubSubProjectId, stream);
	}
}
