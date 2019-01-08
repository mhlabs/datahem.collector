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

import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.ProjectTopicName;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.apphosting.api.ApiProxy;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

// With @WebListener annotation the webapp/WEB-INF/web.xml is no longer required.
@WebListener
public class PubSubClient implements ServletContextListener {
	private static final Logger LOG = LoggerFactory.getLogger(PubSubClient.class);

	private static ServletContext sc;
	//private static Map<String, Publisher> publishers;
	private static LoadingCache<String, Publisher> publishers;
	private static CacheLoader<String, Publisher> loader;
	private static RemovalListener<String, Publisher> removalListener;

	private static void connect(){
		if (publishers == null){
			loader = new CacheLoader<String, Publisher>() {
				@Override
				public Publisher load(String pubSubTopicId) {
					Publisher publisher = null;
					try{
						String pubSubProjectId = ApiProxy.getCurrentEnvironment().getAppId().replaceFirst("^[a-zA-Z]~", "");
						ProjectTopicName topic = ProjectTopicName.of(pubSubProjectId, pubSubTopicId);
						publisher = Publisher
							.newBuilder(topic)
							.build();
						//LOG.info("publisher: " + publisher.toString());
						LOG.info("Cache load: " + publisher.getTopicNameString() + ", ref: " + publisher.toString());
					}catch (Exception e) {
						LOG.error("PubSubClient Connect load error ", e);
					}
					return publisher;
				}
			};
			
			removalListener = new RemovalListener<String, Publisher>() {
				@Override
				public void onRemoval(RemovalNotification<String, Publisher> removal) {
					Publisher publisher = removal.getValue();
					LOG.info("Cache remove: " + publisher.getTopicNameString() + ", ref: " + publisher.toString());
					if (publisher != null) {
						try{
							publisher.shutdown();
							publisher.awaitTermination(20, TimeUnit.SECONDS);
							publisher = null;
						}catch(Exception e){
							LOG.error("PubSubClient Connect load error ", e);
						}
					}
				}
			};
			
			//publishers = new HashMap<String,Publisher>();
			publishers = CacheBuilder
				.newBuilder()
				.maximumSize(1000)
				.removalListener(removalListener)
				.expireAfterAccess(40, TimeUnit.SECONDS)
				.build(loader);
		}
	}

	static Publisher getPublisher(String pubSubTopicId){
		Publisher publisher = null;
		try{
			connect();
			//publisher = publishers.get(pubSubTopicId);
			//LOG.info("pubSubTopic: " + pubSubTopicId);
			publisher = publishers.get(pubSubTopicId);
			//LOG.info("publisher: " + publisher.toString());
		}catch (Exception e) {
			LOG.error("PubSubClient getPublisher error ", e);
		}
		return publisher;
	}

/*
LifecycleManager.getInstance().setShutdownHook(new ShutdownHook() {
  public void shutdown() {
    publishers.invalidateAll();
  }
});*/

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			connect();
		} catch (Exception e) {
			LOG.error("PubSubClient contextInitialized error ", e);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent servletContextEvent) {
	// App Engine does not currently invoke this method.
		publishers.invalidateAll();
		/*
		publishers.forEach((topic,publisher) -> {
			if (publisher != null) {
				try{
					publisher.shutdown();
					publisher.awaitTermination(20, TimeUnit.SECONDS);
					publisher = null;
				}catch(Exception e){
					if (sc != null) {
						sc.log("PubSubClient contextDestroyed error ", e);
					}
				}
			}
		});
		publishers = null;
		*/
	}
}