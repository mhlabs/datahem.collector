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



import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.google.appengine.api.LifecycleManager;
import com.google.appengine.api.LifecycleManager.ShutdownHook;
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
			publisher = publishers.get(pubSubTopicId);
		}catch (Exception e) {
			LOG.error("PubSubClient getPublisher error ", e);
		}
		return publisher;
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			connect();
            //Set up hook to gracefully shutdown publishers when java runtime is shutting down
            LifecycleManager.getInstance().setShutdownHook(new ShutdownHook() {
                public void shutdown() {
                    LOG.info("Executing LifecycleManager shutdownHook to shut down publishers");
                    publishers.invalidateAll();
                }
            });
		} catch (Exception e) {
			LOG.error("PubSubClient contextInitialized error ", e);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent servletContextEvent) {
	// App Engine does not currently invoke this method.
		publishers.invalidateAll();
	}
}
