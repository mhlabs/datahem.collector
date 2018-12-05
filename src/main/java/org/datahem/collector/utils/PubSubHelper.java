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






import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.ApiFutureCallback;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.protobuf.ByteString;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PublisherGrpc;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;

import org.datahem.protobuf.collector.v1.CollectorPayloadEntityProto.*;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.batching.BatchingSettings;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

import com.google.pubsub.v1.ProjectTopicName;

public class PubSubHelper{
	private static final Logger LOG = LoggerFactory.getLogger(PubSubHelper.class);

	public static void publishMessages(List<CollectorPayloadEntity> collectorPayloadEntities, String pubSubProjectId, String pubSubTopicId) throws IOException{
			Publisher publisher = null;
			//List<ApiFuture<String>> messageIdFutures = new ArrayList<>();
			LOG.info("pubSubProjectId:" + pubSubProjectId + ", pubSubTopicId:" + pubSubTopicId);
			/*
			ProjectTopicName topic = ProjectTopicName.newBuilder()
				.setProject(pubSubProjectId)
				.setTopic(pubSubTopicId)
				.build();*/
			//ProjectTopicName topic = ProjectTopicName.of(pubSubProjectId, pubSubTopicId);
			String topic = "projects/"+ pubSubProjectId +"/topics/" + pubSubTopicId;
			//TopicName topic = TopicName.create(pubSubProjectId, pubSubTopicId);
	
			try {
				// Batch settings control how the publisher batches messages
				long requestBytesThreshold = 5000L; // default : 1kb
				long messageCountBatchSize = 1L; // default : 100
				Duration publishDelayThreshold = Duration.ofMillis(1); // default : 1 ms

				// Publish request get triggered based on request size, messages count & time since last publish
				BatchingSettings batchingSettings = BatchingSettings.newBuilder()
					.setElementCountThreshold(messageCountBatchSize)
					.setRequestByteThreshold(requestBytesThreshold)
					.setDelayThreshold(publishDelayThreshold)
					.build();
				
			  	publisher = Publisher
			  		.newBuilder(topic)
			  		.setBatchingSettings(batchingSettings)
			  		.build();
			  		
			  	  // schedule publishing one message at a time : messages get automatically batched
				for (CollectorPayloadEntity collectorPayloadEntity : collectorPayloadEntities) {
					PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
	               		.putAllAttributes(
	               			ImmutableMap.<String, String>builder()
	               				.put("timestamp", collectorPayloadEntity.getEpochMillis())
	               				.put("pubSubProjectId", pubSubProjectId)
	               				.put("pubSubTopicId", pubSubTopicId)
	               				.put("uuid", collectorPayloadEntity.getUuid())
	               				.build() 
	                    )
	                    .setData(collectorPayloadEntity.toByteString())
						.build();
						
				    // Once published, returns a server-assigned message id (unique within the topic)
				    publisher.publish(pubsubMessage);
				}
			} finally {
		  		if (publisher != null) {
			  		try{
			    		publisher.shutdown();
			    		publisher.awaitTermination(1, TimeUnit.MINUTES); //PublisherSnippets.java
			    	}catch(Exception e){
			    		System.out.print("Exception: ");
	        			System.out.println(e.getMessage());
			    	}
			  } 
			}
		}
}
