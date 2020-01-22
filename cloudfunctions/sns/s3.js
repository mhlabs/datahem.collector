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

var aws = require('aws-sdk');

module.exports.s3reader = async function(_accessKeyId, _secretAccessKey, _awsS3Bucket, _awsS3Key){
    console.info('key: ***' + _accessKeyId.substr(-4) + ', secret: ***' + _secretAccessKey.substr(-4) + ', s3bucket: ' + _awsS3Bucket + ', s3key: ' + _awsS3Key);
    var s3 = new aws.S3({ accessKeyId: _accessKeyId, secretAccessKey: _secretAccessKey });
    //console.info('inside s3.js');
    var getParams = {
        Bucket: _awsS3Bucket,
        Key: _awsS3Key
    }
    const data = await s3.getObject(getParams).promise().catch((error) => {
        console.error(error);
        console.error("Access to S3 object denied?");
    });
    //console.info(data.Body);
    const body = JSON.parse(data.Body.toString("utf8"));
    const message = JSON.parse(body.Message);
    return message;
}