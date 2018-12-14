package org.datahem.collector.utils;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PublisherGrpc;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PubsubMessage;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.threeten.bp.Duration;

public class PubSubHelper{
	private static final Logger LOG = LoggerFactory.getLogger(PubSubHelper.class);

	public static void publishMessage(PubsubMessage pubsubMessage, String pubSubProjectId, String pubSubTopicId) throws IOException{
			Publisher publisher = null;
			ProjectTopicName topic = ProjectTopicName.of(pubSubProjectId, pubSubTopicId);
	
			try {
				publisher = Publisher
					.newBuilder(topic)
					.build();
					
				publisher.publish(pubsubMessage).get(); 
			}
			catch(Exception e){LOG.error("uuid: " + pubsubMessage.getAttributes().get("MessageUuid") + ", Message: " + pubsubMessage.toString() + " Exception: " + e.getMessage());}
			finally {
				if (publisher != null) {
					try{
						publisher.shutdown();
						publisher.awaitTermination(1, TimeUnit.MINUTES);
					}catch(Exception e){
						LOG.error("Exception: "+ e.getMessage());
					}
				}
			}
		}
}
