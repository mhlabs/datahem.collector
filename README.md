# datahem.collector

mvn endpoints-framework:openApiDocs -Dendpoints.hostname=$PROJECT_ID.appspot.com
gcloud endpoints services deploy target/openapi-docs/openapi.json
mvn -Dendpoints.project.id=$PROJECT_ID appengine:deploy