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

module.exports.decrypt = async function(
  projectId, // Your GCP projectId
  keyRingId, // Name of the KMS key ring
  cryptoKeyId, // Name of the KMS key
  ciphertext
) 
{
  // Import the library and create a client
  const kms = require('@google-cloud/kms');
  const client = new kms.KeyManagementServiceClient();

  // The location of the crypto key's key ring, e.g. "global"
  const locationId = 'global';
  const name = client.cryptoKeyPath(
    projectId,
    locationId,
    keyRingId,
    cryptoKeyId
  );

  // Decrypts text using the specified crypto key
  const result = await client.decrypt({name, ciphertext});
  const plaintext = result[0].plaintext.toString("utf8");
  //console.info('plaintext.length: ' + plaintext.length);
  return plaintext;
}
// [END kms_decrypt]