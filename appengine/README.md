# datahem.collector

Collect data sent from trackers and publish the data on pubsub, currently running on Google App Engine Standard (Java8) and Cloud Endpoints.

## Version
## 0.10.3 (2019-03-22): AGPL 3.0 or later
License is AGPL 3.0 or later

## 0.10.2 (2019-01-09): Gif servlet
Added ordinary servlet (not endpoint framework) to respond with a transparent gif on image get requests to ensure correct response code.

## 0.10.1 (2019-01-09): Shutdownhook to close publishers
Added shutdown hook to gracefully shutdown publishers when java runtime is shutting down.

## 0.10.0 (2019-01-09): Restricted access with API keys and new api method paths
Added method for restricted access (POST) with API keys to enable control over webhooks to post data to the collector endpoint.
Changed naming of API method (name and path) to reflect usage, i.e. open vs restricted.

## 0.9.0 (2018-12-20): Cache and expire publishers and MP headers in pubsub message attributes
Cache and expire publishers to avoid orphaned grpc channels while utilizing the high performance of "long lived" publishers.
Swithed from appending request headers to request body to adding headers to pubsub attributes.

## 0.8.1 (2018-12-18): Long lived publisher
Changed from creating and shutting down publishers with each call to longlived publishers created by context listener. Reduced deadline exceptions and response latency substantially. Less instances required to server the same amount of traffic.

## 0.8.0 (2018-12-14): Changed from collector payload entity to querystring payload
Changed from collector payload entity to querystring payload for faster perfomance and shut down used resources for better stability. Also cleaned up code and dependencies.

## 0.7.0 (2018-06-14): Cloud Endpoints and Camel Case
Changed the collector to use cloud endpoints for better security, monitoring, logging and RESTful URLs