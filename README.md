# datahem.collector

mvn endpoints-framework:openApiDocs -Dendpoints.project.id=$PROJECT_ID
gcloud endpoints services deploy target/openapi-docs/openapi.json
mvn -Dendpoints.project.id=$PROJECT_ID appengine:deploy