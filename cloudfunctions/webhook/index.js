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

// import the Google Cloud Pubsub client library
const {PubSub} = require('@google-cloud/pubsub');

// our pubsub client
const pubsub = new PubSub(); //modified

const util = require('util');

var backupTopic = process.env.BACKUP_TOPIC;

/**
 * Cloud Function.
 *
 * @param {req} request The web request from client.
 * @param {res} The response returned from this function.
 */
exports.webhook = async function webhook (req, res) {
    var payload;
    var attributes = new Map([
        ['topic',req.query.topic],
        ['timestamp',new Date().toISOString()],
        ['uuid','abc123']
    ]);
    var headerMap = new Map(Object.entries(req.headers));
    attributes = new Map([...attributes, ...headerMap]);
    if (req.method === 'POST') {
        payload = req.body;
        var queryStringMap = new Map(Object.entries(req.query));
        attributes = new Map([...attributes, ...queryStringMap]);
    } else{
        var i = req.url.indexOf('?');
        payload = req.url.substr(i+1);
    }
    console.info(`req.query.topic: ${req.query.topic} , payload: ${payload}`);
    console.info(util.inspect(attributes, {showHidden: false, depth: null}));
    
    // the cloud pubsub topic we will publish messages to
    var topicName = req.query.topic;
    if(topicName !== undefined){
        const topic = pubsub.topic(topicName);
        const backup = pubsub.topic(backupTopic);
        
        /*
        const attributes = {
            timestamp: message.Timestamp,
            topic: topicName || "#",
            uuid: message.MessageId
        };
        */

        var errCheck = false;    
        var msgData = Buffer.from(payload);
        
        /*
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
        */
        if(errCheck){
            console.error(`error when publishing data object to pubsub topic ${topicName}`);
            res.status(400).end(`error when publishing data object to pubsub topic ${topicName}`); 
        } else {
            res.status(200).end('ok');
        } 
    }else{
        console.error("topic query param undefined");
        res.status(400).end('topic query param undefined');    
    }
};