gcloud beta functions deploy receiveNotification --region europe-west1 --trigger-http --stage-bucket sns-cf --set-env-vars ACCOUNT_PATTERN=<aws-account>

Examples:
test: gcloud beta functions deploy receiveNotification --region europe-west1 --trigger-http --stage-bucket sns-cf --set-env-vars ACCOUNT_PATTERN=25134460778
prod: gcloud beta functions deploy receiveNotification --region europe-west1 --trigger-http --stage-bucket sns-cf --set-env-vars ACCOUNT_PATTERN=42520738248

Set up a SNS HTTPS subscriber endpoint that points to the cloud function url: https://<region>-<project>.cloudfunctions.net/receiveNotification?topic=<my-pubsub-topic>
Create corresponding pubsub topic and subscriber/s on gcp

## Version
## 0.2.0 (2019-04-02): Query parameter
specify pubsub topic with query parameter instead of sns subject field.

## 0.1.0 (2019-03-22): First release
First release
License is AGPL 3.0 or later