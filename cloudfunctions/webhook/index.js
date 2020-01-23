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

'use strict';

// import the Google Cloud Pubsub client library
const {PubSub} = require('@google-cloud/pubsub');

const pubsub = new PubSub();
const TRANSPARENT_GIF_BUFFER = Buffer.from('R0lGODlhAQABAIAAAP///wAAACwAAAAAAQABAAACAkQBADs=', 'base64');

const util = require('util');
const uuidv4 = require('uuid/v4');

var backupTopic = process.env.BACKUP_TOPIC;

/**
 * Cloud Function.
 *
 * @param {req} request The web request from client.
 * @param {res} The response returned from this function.
 */

async function publish(res, topicName, backupTopic, payload, attributes){
    const topic = pubsub.topic(topicName);
    const backup = pubsub.topic(backupTopic);

    var msgData = Buffer.from(payload);
    await Promise.all([
            backup.publish(msgData, attributes),
            topic.publish(msgData, attributes)
        ])
        .catch(function(err) {
            console.error(err.message);
            res.status(400).end(`error when publishing data object to pubsub`); 
        });
}

exports.webhook = async function webhook (req, res) {
        var topicName = req.query.topic;
        if(topicName !== undefined){
            var payload;
            var attributes = {
                topic : req.query.topic,
                timestamp :  new Date().toISOString(),
                uuid : uuidv4()
            };
            attributes = {...attributes, ...req.headers};
            res.set('Access-Control-Allow-Origin', '*');
            res.set('Access-Control-Allow-Methods', 'POST, GET, OPTIONS, HEAD');
            //res.set('Access-Control-Allow-Headers', Object.keys(req.headers).join());
            res.set('Access-Control-Max-Age', '3600');
            switch(req.method){
                case 'POST':
                    payload = req.body;
                    attributes = {...attributes, ...req.query};
                    await publish(res, topicName, backupTopic, payload, attributes);
                    if(!res.headersSent){
                        res.status(204).end();
                    }
                    break;
                case 'GET':
                    var i = req.url.indexOf('?');
                    payload = req.url.substr(i+1);
                    await publish(res, topicName, backupTopic, payload, attributes);
                    if(!res.headersSent){
                        res.writeHead(200, { 'Content-Type': 'image/gif' });
                        res.end(TRANSPARENT_GIF_BUFFER, 'binary');
                    }
                    break;
                case 'OPTIONS':
                    res.status(204).send('');
                    break;
            }
        }else{
            console.error("topic query param undefined");
            res.status(400).end('topic query param undefined');    
        }
};