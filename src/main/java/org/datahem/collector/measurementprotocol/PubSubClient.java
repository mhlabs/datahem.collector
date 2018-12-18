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

// With @WebListener annotation the webapp/WEB-INF/web.xml is no longer required.
@WebListener
public class PubSubClient implements ServletContextListener {

	private static ServletContext sc;
	private static Map<String, Publisher> publishers;

	private static void connect(){
		if (publishers == null){
			publishers = new HashMap<String,Publisher>();
		}
	}

	static Publisher getPublisher(String pubSubProjectId, String pubSubTopicId){
		connect();
		Publisher publisher = publishers.get(pubSubTopicId);
			
		if(publisher == null){
			try{
				ProjectTopicName topic = ProjectTopicName.of(pubSubProjectId, pubSubTopicId);
				publisher = Publisher
					.newBuilder(topic)
					.build();
				publishers.put(pubSubTopicId,publisher);
			}catch (Exception e) {
				if (sc != null) {
					sc.log("PubSubClient getPublisher error ", e);
				}
			}
		}
		return publisher;
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			connect();
		} catch (Exception e) {
		if (sc != null) {
				sc.log("PubSubClient contextInitialized error ", e);
			}
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent servletContextEvent) {
	// App Engine does not currently invoke this method.
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
	}
}