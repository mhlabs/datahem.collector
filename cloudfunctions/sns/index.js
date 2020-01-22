/*-
 * Datahem collector cloud functions sns
 * 
 * Copyright (C) 2018 - 2019 MatHem i Sverige AB
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

 /*
 * This file is a derivative work of https://github.com/GoogleCloudPlatform/community/blob/master/tutorials/cloud-functions-sns-pubsub/index.js by  Preston Holmes released under Apache 2.0.
 * Changes to this file includes: 
 * - a newer version of pubsub library and modifications to use sns subject as pubsub topic to allow for more generic use.
 * - aws account pattern rather than explicit topicarn to allow a more than one https subscribers within the same aws account. Aws account is passed along as environment variable.
 */

'use strict';

// We use the https library to confirm the SNS subscription
const https = require('https');

// import the Google Cloud Pubsub client library
const {PubSub} = require('@google-cloud/pubsub');

// the sns-validator package verifies the host an signature of SNS messages
var MessageValidator = require('sns-validator');
var validator = new MessageValidator();

var kmsDecryptor = require('./kms_decrypt.js');
var bucketReader = require('./s3.js');

// our pubsub client
const pubsub = new PubSub(); //modified

var accountPattern = RegExp(process.env.ACCOUNT_PATTERN); //modified
var projectId = process.env.KMS_PROJECT_ID || process.env.GCP_PROJECT;
var keyRingId = process.env.KMS_KEY_RING_ID;
var cryptoKeyId = process.env.KMS_CRYPTO_KEY_ID;
var secretAccessKeyEncrypted = process.env.SECRET_ACCESS_KEY_ENCRYPTED;
var accessKeyIdEncrypted = process.env.ACCESS_KEY_ID_ENCRYPTED;
var backupTopic = process.env.BACKUP_TOPIC;

/**
 * Cloud Function.
 *
 * @param {req} request The web request from SNS.
 * @param {res} The response returned from this function.
 */
exports.receiveNotification = async function receiveNotification (req, res) {
  // we only respond to POST method HTTP requests
  if (req.method !== 'POST') {
    res.status(405).end('only post method accepted');
    return;
  }
    
  // all valid SNS requests should have this header
  var snsHeader = req.get('x-amz-sns-message-type');
  if (snsHeader === undefined) {
    res.status(403).end('invalid SNS message');
    return;
  }

  // use the sns-validator library to verify signature
  // we first parse the cloud function body into a javascript object
  validator.validate(JSON.parse(req.body), async function (err, message) {
    if (err) {
      // the message did not validate
      res.status(403).end('invalid SNS message');
      return;
    }
    if (!accountPattern.test(message.TopicArn)) { //modified
      // we got a request from a topic we were not expecting to
      // this sample is set up to only receive from one specified SNS topic
      // one could adapt this to accept an array, but if you do not check
      // the origin of the message, anyone could end up publishing to your
      // cloud function
      res.status(403).end('invalid SNS Accountid');
      return;
    }
    // here we handle either a request to confirm subscription, or a new message
    switch (message.Type.toLowerCase()) {
      case 'subscriptionconfirmation':
        console.info('confirming subscription ' + message.SubscribeURL);
        // SNS subscriptions are confirmed by requesting the special URL sent by the service as a confirmation
        https.get(message.SubscribeURL, (subRes) => {
          console.info('statusCode:', subRes.statusCode);
          console.info('headers:', subRes.headers);

          subRes.on('data', (d) => {
            console.info(d);
            res.status(200).end('ok');
          });
        }).on('error', (e) => {
          console.error(e);
          res.status(500).end('confirmation failed');
        });
        break;
        //end subscriptionconfirmation and break switch
      
      case 'notification':
        // this is a regular SNS notice, we relay to Pubsub
        console.info(`req.query.topic: ${req.query.topic} , message.Timestamp: ${message.Timestamp}, message.MessageId: ${message.MessageId} `);
        // the cloud pubsub topic we will publish messages to
        var topicName = req.query.topic;
        if(topicName !== undefined){
            const topic = pubsub.topic(topicName);
            const backup = pubsub.topic(backupTopic);
            const attributes = {
                timestamp: message.Timestamp,
                topic: topicName || "#",
                uuid: message.MessageId
            };
            var awsS3Bucket = (((message || {}).MessageAttributes || {}).PubSub_S3Bucket || {}).Value;
            var awsS3Key = (((message || {}).MessageAttributes || {}).PubSub_S3Key || {}).Value;
            var errCheck = false;
            //console.info(attributes);
            if(awsS3Bucket !== undefined && awsS3Key !== undefined){
                // SNS notification with large payload to read from S3
                console.info(`Get ${topicName} message from bucket ${awsS3Bucket} and key ${awsS3Key} `);
                var _accessKeyId = await kmsDecryptor.decrypt(projectId, keyRingId, cryptoKeyId, accessKeyIdEncrypted).catch(console.error);
                var _secretAccessKey = await kmsDecryptor.decrypt(projectId, keyRingId, cryptoKeyId, secretAccessKeyEncrypted).catch(console.error);
                var bucketData = await bucketReader.s3reader(_accessKeyId, _secretAccessKey, awsS3Bucket, awsS3Key);
                if(Array.isArray(bucketData)){
                // S3 object is a list of SNS notifications
                    console.info('Is S3 batch (array) of ' + bucketData.length + 'data objects');
                    var idx = 0;
                    bucketData.forEach(async function(arrayItem){
                        const msgAttributes = {
                            timestamp: message.Timestamp,
                            topic: topicName || "#",
                            uuid: message.MessageId + "-" + idx
                        };
                        var newImageId = ((arrayItem || {}).NewImage || {}).Id;
                        var oldImageId = ((arrayItem || {}).OldImage || {}).Id;
                        console.info(`topic: ${topicName} , timestamp: ${msgAttributes.timestamp}, messageid: ${msgAttributes.timestamp} , newImageId: ${newImageId}, oldImageId: ${oldImageId} , s3bucket ${awsS3Bucket} , s3key ${awsS3Key}`);
                        var msgData = Buffer.from(JSON.stringify(arrayItem));
                        await backup.publish(msgData, msgAttributes)
                            .catch((error) => {
                                console.error('PubSub publish to backup failed.');
                                console.error(error);
                                errCheck = true;
                            });
                        await topic.publish(msgData, msgAttributes)
                            .catch((error) => {
                                console.error(`PubSub publish to ${topicName} backup failed.`);
                                console.error(error);
                                errCheck = true;
                            });
                        idx++;
                    });
                    if(errCheck){
                        console.error(`error when publishing batch of s3 data objects to pubsub topic ${topicName}`);
                        res.status(400).end(`error when publishing batch of s3 data objects to pubsub topic ${topicName}`); 
                    } else {
                        res.status(200).end('ok');
                    }                    
                } else {
                    // S3 object is a single SNS notification
                    console.info('Is single S3 data object');
                    var newImageId = ((bucketData || {}).NewImage || {}).Id;
                    var oldImageId = ((bucketData || {}).OldImage || {}).Id;
                    console.info(`topic: ${topicName} , timestamp: ${attributes.timestamp}, messageid: ${attributes.uuid} , newImageId: ${newImageId}, oldImageId: ${oldImageId} , s3bucket ${awsS3Bucket} , s3key ${awsS3Key}`);
                    var msgData = Buffer.from(JSON.stringify(bucketData));
                    await backup.publish(msgData, attributes)
                        .catch((error) => {
                            console.error('PubSub publish to backup failed.');
                            console.error(error);
                            errCheck = true;
                        });
                    await topic.publish(msgData, attributes)
                        .catch((error) => {
                            console.error(`PubSub publish to ${topicName} backup failed.`);
                            console.error(error);
                            errCheck = true;
                        });
                    if(errCheck){
                        console.error(`error when publishing single s3 data object to pubsub topic ${topicName}`);
                        res.status(400).end(`error when publishing single s3 data object to pubsub topic ${topicName}`); 
                    } else {
                        res.status(200).end('ok');
                    }
                }
            } else {
                // ordinary SNS message with data in payload
                var ms = JSON.parse(message.Message); 
                var newImageId = ((ms || {}).NewImage || {}).Id;
                var oldImageId = ((ms || {}).OldImage || {}).Id;
                console.info(`topic: ${topicName} , timestamp: ${attributes.timestamp}, messageid: ${attributes.uuid} , newImageId: ${newImageId}, oldImageId: ${oldImageId} `);
                var msgData = Buffer.from(message.Message);
                await backup.publish(msgData, attributes)
                    .catch((error) => {
                        console.error('PubSub publish to backup failed.');
                        console.error(error);
                        errCheck = true;
                    });
                await topic.publish(msgData, attributes)
                    .catch((error) => {
                        console.error(`PubSub publish to ${topicName} backup failed.`);
                        console.error(error);
                        errCheck = true;
                    });
                if(errCheck){
                    console.error(`error when publishing single sns message to pubsub topic ${topicName}`);
                    res.status(400).end(`error when publishing single sns message to pubsub topic ${topicName}`); 
                } else {
                    res.status(200).end('ok');
                }
            }
        }else{
            console.error("topic query param undefined");
            res.status(400).end('topic query param undefined');    
        }
        break;
        //end notification and break switch
      default:
        console.error('should not have gotten to default block');
        res.status(400).end('invalid SNS message or pubsub topic missing that matches the topic query parameter');
    }
  });
};