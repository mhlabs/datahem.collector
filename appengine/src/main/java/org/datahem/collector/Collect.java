package org.datahem.collector.measurementprotocol;

/*-
 * ========================LICENSE_START=================================
 * DataHem
 * %%
 * Copyright (C) 2018 - 2019 Robert Sahlin
 * %%
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
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
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.ProjectTopicName;

import javax.servlet.http.HttpServletRequest;

import java.net.URLEncoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

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
 * The Collect API which Endpoints will be exposing.
 */
// [START collect_api_annotation]
@Api(
    name = "collect",
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
   * Collects data and publish on pubSub. Returns 204 on success.
   */
    // [START collect_post]
	@ApiMethod(name = "open_post", path = "open/{stream}", httpMethod = ApiMethod.HttpMethod.POST)
	public void _open_post(HttpServletRequest req, Payload payload, @Named("stream") String stream) throws IOException {
		buildCollectorPayload(payload.getPayload(), req, stream);
	}
    // [END collect_post]

    // [START collect_get_gif]
	@ApiMethod(name = "open_get_gif", path = "open/{stream}/collect.gif", httpMethod = ApiMethod.HttpMethod.GET)
	public void _open_get_gif(HttpServletRequest req, @Named("stream") String stream) throws IOException{
		String payload = req.getQueryString();
		if (!"".equals(payload)) {
			buildCollectorPayload(payload, req, stream);
		}
    }
    // [END collect_get_gif]

    // [START collect_restricted_post]
    //"${HOST}/_ah/api/collect/v1/restricted/${STREAM_NAME}?key=${API_KEY}"
	@ApiMethod(name = "restricted_post", path = "restricted/{stream}", httpMethod = ApiMethod.HttpMethod.POST, apiKeyRequired = AnnotationBoolean.TRUE)
	public void _restricted_post(HttpServletRequest req, Payload payload, @Named("stream") String stream) throws IOException {
		buildCollectorPayload(payload.getPayload(), req, stream);
	}
    // [END collect_restricted_post]

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
