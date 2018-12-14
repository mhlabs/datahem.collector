# datahem.collector

Collect data sent from trackers and publish the data on pubsub, currently running on Google App Engine Standard (Java8) and Cloud Endpoints.

## Version
## 0.8 (2018-12-14): Changed from collector payload entity to querystring payload
Changed from collector payload entity to querystring payload for faster perfomance and shut down used resources for better stability. Also cleaned up code and dependencies.

## 0.7 (2018-06-14): Cloud Endpoints and Camel Case
Changed the collector to use cloud endpoints for better security, monitoring, logging and RESTful URLs