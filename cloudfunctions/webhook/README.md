```bash
gcloud beta functions deploy webhook --region europe-west1 --trigger-http --stage-bucket <bucket> --runtime nodejs10 --max-instances 10 --set-env-vars BACKUP_TOPIC=<backup-topic>
```
Set up a webhook endpoint that points to the cloud function url: https://<region>-<project>.cloudfunctions.net/webhook?topic=<my-pubsub-topic>
Create corresponding pubsub topic and subscriber/s on gcp.
Make sure to use datahem.tracker/measurement_protocol/variables/GaCustomTask.js in google tag manager when tracking GA-data.


## Version

## 0.1.0 (2019-03-22): First release
First release