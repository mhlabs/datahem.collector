```bash
gcloud beta functions deploy receiveNotification --region europe-west1 --trigger-http --stage-bucket <bucket> --set-env-vars ACCOUNT_PATTERN=<aws-account>

Examples:

gcloud beta functions deploy webhook --region europe-west1 --trigger-http --stage-bucket <bucket> --runtime nodejs10 --max-instances 10 --set-env-vars BACKUP_TOPIC=backup
```
Set up a SNS HTTPS subscriber endpoint that points to the cloud function url: https://<region>-<project>.cloudfunctions.net/webhook?topic=<my-pubsub-topic>
Create corresponding pubsub topic and subscriber/s on gcp


## Version

## 0.1.0 (2019-03-22): First release
First release