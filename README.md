# datahem.collector

Collect data sent from trackers and publish the data on pubsub, currently running on Google App Engine Standard (Java8) and Cloud Endpoints.

## Version
## 0.9.0 (2018-12-20): Cache and expire publishers and MP headers in pubsub message attributes
Cache and expire publishers to avoid orphaned grpc channels while utilizing the high performance of "long lived" publishers.
Swithed from appending request headers to request body to adding headers to pubsub attributes.

## 0.8.1 (2018-12-18): Long lived publisher
Changed from creating and shutting down publishers with each call to longlived publishers created by context listener. Reduced deadline exceptions and response latency substantially. Less instances required to server the same amount of traffic.

## 0.8.0 (2018-12-14): Changed from collector payload entity to querystring payload
Changed from collector payload entity to querystring payload for faster perfomance and shut down used resources for better stability. Also cleaned up code and dependencies.

## 0.7.0 (2018-06-14): Cloud Endpoints and Camel Case
Changed the collector to use cloud endpoints for better security, monitoring, logging and RESTful URLs