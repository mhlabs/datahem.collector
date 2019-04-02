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
 * - a newer version of pubsub library and modifications to use topic query parameter as pubsub topic to allow for more generic use.
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

// our pubsub client
const pubsub = new PubSub(); //modified

var accountPattern = RegExp(process.env.ACCOUNT_PATTERN); //modified

/**
 * Cloud Function.
 *
 * @param {req} request The web request from SNS.
 * @param {res} The response returned from this function.
 */
exports.receiveNotification = function receiveNotification (req, res) {
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
  validator.validate(JSON.parse(req.body), function (err, message) {
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

    // here we handle either a request to confirm subscription, or a new
    // message
    switch (message.Type.toLowerCase()) {
      case 'subscriptionconfirmation':
        console.info('confirming subscription ' + message.SubscribeURL);
        // SNS subscriptions are confirmed by requesting the special URL sent
        // by the service as a confirmation
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
      case 'notification':
        // this is a regular SNS notice, we relay to Pubsub
        //console.info(message.MessageId + ': ' + message.Message);

        // the cloud pubsub topic we will publish messages to
        var topicName = req.query.topic;

        //modified
        const attributes = {
          timestamp: message.Timestamp,
          topic: topicName || "#",
          uuid: message.MessageId
        };
        //console.info(attributes);

        var msgData = Buffer.from(message.Message);
        
        //modified
        if(topicName !== undefined){
            pubsub.topic(topicName).publish(msgData, attributes).then(function (results) {
                //console.info('message published ' + results[0]);
                res.status(200).end('ok');
            })
            .catch((e) => {
                console.error(e);
                res.status(400).end('pubsub publish error. Missing pubsub topic?');
            });
        }else{
            console.error("topic query param undefined");
            res.status(400).end('topic query param undefined');    
        }
        
        break;
      default:
        console.error('should not have gotten to default block');
        res.status(400).end('invalid SNS message or pubsub topic missing that matches the topic query parameter');
    }
  });
};