#!/bin/bash
#
# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


#################################################
#        USER CONFIGURATION SECTION             #
#################################################

# Project Id of the Google Cloud Project
PROJECT_ID=""

# Google Cloud Region to use for deployment of resources
# Refer to https://cloud.google.com/compute/docs/regions-zones#available
REGION=""

CLOUD_RUN_SERVICE_NAME="bq-tokenize-fns"

BQ_FUNCTION_DATASET="fns"

CLOUD_SECRET_KEY_NAME="${CLOUD_RUN_SERVICE_NAME}-aes-key"

# Can be 'UTF_8_KEY' OR 'BASE64_KEY'
AES_KEY_TYPE="BASE64_KEY"

# Use appropriate value based on AES_KEY_TYPE
AES_ENCRYPTION_KEY_BASE64="$(dd if=/dev/urandom count=1 bs=16 | base64)"

#The Default Cipher to use.
AES_CIPHER_TYPE="AES/CBC/PKCS5PADDING"

CLOUD_SECRET_IV_NAME="${CLOUD_RUN_SERVICE_NAME}-iv"

# Default IV parameter
AES_IV_PARAMETER_BASE64="$(dd if=/dev/urandom count=1 bs=16 | base64)"


#################################################
#       EXECUTION SCRIPT. DON'T EDIT            #
#################################################

echo "setting Google Cloud Project"
gcloud config set project ${PROJECT_ID}

echo "Enabling Google Cloud Services."

gcloud services --project ${PROJECT_ID} enable \
bigquery.googleapis.com \
bigqueryconnection.googleapis.com \
cloudbuild.googleapis.com \
cloudkms.googleapis.com \
containerregistry.googleapis.com \
dlp.googleapis.com \
run.googleapis.com \
secretmanager.googleapis.com

echo "Creating Cloud Secrets Key for AES Key"
printf '%s' "${AES_ENCRYPTION_KEY_BASE64}" | \
gcloud secrets create ${CLOUD_SECRET_KEY_NAME} \
--data-file=- \
--locations="${REGION}" \
--replication-policy=user-managed \
--project="${PROJECT_ID}"

echo "Creating Cloud Secret for storing IV"
printf '%s' "${AES_IV_PARAMETER_BASE64}" | \
gcloud secrets create ${CLOUD_SECRET_IV_NAME} \
--data-file=- \
--locations="${REGION}" \
--replication-policy=user-managed \
--project="${PROJECT_ID}"


echo "Grant Secret Accessor to Cloud Run Service Acco"

COMPUTE_ENGINE_SA="$(gcloud iam service-accounts list --format json | jq -r '.[] | select(.displayName="Default compute service account") | .email')"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
--member="serviceAccount:${COMPUTE_ENGINE_SA}" \
--role='roles/secretmanager.secretAccessor'

echo "Building Code (using Cloud Build)"

gcloud builds submit \
--project "${PROJECT_ID}" \
--region "${REGION}" \
--tag "gcr.io/${PROJECT_ID}/${CLOUD_RUN_SERVICE_NAME}" \
--machine-type=e2-highcpu-8

echo "Deploying on Cloud Run"

gcloud beta run deploy ${CLOUD_RUN_SERVICE_NAME} \
--image="gcr.io/${PROJECT_ID}/${CLOUD_RUN_SERVICE_NAME}:latest" \
--execution-environment=gen2 \
--platform=managed \
--region="${REGION}" \
--update-env-vars=PROJECT_ID=${PROJECT_ID} \
--update-env-vars=AES_KEY_TYPE=${AES_KEY_TYPE} \
--update-env-vars=AES_CIPHER_TYPE=${AES_CIPHER_TYPE} \
--update-secrets=AES_KEY=${CLOUD_SECRET_KEY_NAME}:latest \
--update-secrets=AES_IV_PARAMETER_BASE64=${CLOUD_SECRET_IV_NAME}:latest \
--no-allow-unauthenticated \
--project "${PROJECT_ID}"

echo "Extracting Cloud Run Endpoint URL"

RUN_URL="$(gcloud run services describe ${CLOUD_RUN_SERVICE_NAME} --region ${REGION} --project ${PROJECT_ID} --format json | jq -r '.status.address.url')"
echo "Found Endpoint: ${RUN_URL}"

echo "Create BigQuery Connection"

bq mk --connection \
--display_name='External Tokenization Function Connection' \
--connection_type=CLOUD_RESOURCE \
--project_id="${PROJECT_ID}" \
--location="${REGION}" \
ext-${CLOUD_RUN_SERVICE_NAME}

echo "Extracting BigQuery connection Service Account"

CONNECTION_SA="$(bq --project_id ${PROJECT_ID} --format json show --connection ${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME} | jq -r '.cloudResource.serviceAccountId')"

echo "Assigning role/run.invoker for serviceAccount: ${CONNECTION_SA}"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
--member="serviceAccount:${CONNECTION_SA}" \
--role='roles/run.invoker'

echo "Checking if Function Dataset (${PROJECT_ID}:${BQ_FUNCTION_DATASET}) exists"
BQ_FN_DATASET_ERROR=$(bq show --project_id ${PROJECT_ID} --dataset ${PROJECT_ID}:${BQ_FUNCTION_DATASET} | grep error)

if [ "${BQ_FN_DATASET_ERROR}" ]
then
    echo "Creating Function Dataset (${PROJECT_ID}:${BQ_FUNCTION_DATASET})"
    bq mk --dataset --project_id ${PROJECT_ID} --location ${REGION} ${BQ_FUNCTION_DATASET}
fi

echo "Creating BigQuery Remote Functions"

echo "Base64 Functions"

bq query --project_id ${PROJECT_ID} --use_legacy_sql=false "CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.ext_encode64(v STRING) RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME}\`
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'tokenize'),('algo','base64')]);"

bq query --project_id ${PROJECT_ID} --use_legacy_sql=false "CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.ext_decode64(v STRING) RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME}\`   
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'reidentify'),('algo','base64')]);"


echo "Creating AES Functions"

bq query --project_id ${PROJECT_ID} --use_legacy_sql=false "CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.aes128ecb_encrypt(v STRING) RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME}\`
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'tokenize'),('algo','aes'),('aes-cipher-type','AES/ECB/PKCS5PADDING')]);"

bq query --project_id ${PROJECT_ID} --use_legacy_sql=false "CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.aes128ecb_decrypt(v STRING) RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME}\`
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'reidentify'),('algo','aes'),('aes-cipher-type','AES/ECB/PKCS5PADDING')]);"

bq query --project_id ${PROJECT_ID} --use_legacy_sql=false "CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.aes128cbc_encrypt(v STRING) RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME}\`
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'tokenize'),('algo','aes'),('aes-cipher-type','AES/CBC/PKCS5PADDING'),('aes-iv-parameter-base64','VGhpc0lzVGVzdFZlY3Rvcg==')]);"

bq query --project_id ${PROJECT_ID} --use_legacy_sql=false "CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.aes128cbc_decrypt(v STRING) RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME}\`
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'reidentify'),('algo','aes'),('aes-cipher-type','AES/CBC/PKCS5PADDING'),('aes-iv-parameter-base64','VGhpc0lzVGVzdFZlY3Rvcg==')]);"


echo "Creating DLP DeIdentify Template"
DEID_TEMPLATE=$(curl -X POST \
-H "Authorization: Bearer $(gcloud auth print-access-token)" \
-H "Accept: application/json" \
-H "Content-Type: application/json" \
-H "X-Goog-User-Project: ${PROJECT_ID}" \
--data-binary "@sample_dlp_deid_config.json" \
"https://dlp.googleapis.com/v2/projects/${PROJECT_ID}/locations/${REGION}/deidentifyTemplates")

DEID_TEMPLATE_NAME=$(echo "${DEID_TEMPLATE}" | jq -r '.name')

echo "Created DEID template: ${DEID_TEMPLATE_NAME}"

echo "Creating DLP Functions"

bq query --project_id ${PROJECT_ID} --use_legacy_sql=false "CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.dlp_freetext_encrypt(v STRING) RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-${CLOUD_RUN_SERVICE_NAME}\`
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'tokenize'),('algo','dlp'),('dlp-deid-template','${DEID_TEMPLATE_NAME}')]);"

bq query --project_id ${PROJECT_ID} \
--use_legacy_sql=false \
"CREATE OR REPLACE FUNCTION ${BQ_FUNCTION_DATASET}.dlp_freetext_decrypt(v STRING)
RETURNS STRING
REMOTE WITH CONNECTION \`${PROJECT_ID}.${REGION}.ext-bq-tokenize-fn\`
OPTIONS (endpoint = '${RUN_URL}', user_defined_context = [('mode', 'reidentify'),('algo','dlp'),('dlp-deid-template','${DEID_TEMPLATE_NAME}')]);"

echo "All Resources Created."

echo "Run the following Query in your BigQuery Workspace"
echo "
SELECT
  pii_column,
  ${BQ_FUNCTION_DATASET}.dlp_freetext_encrypt(pii_column) AS dlp_encrypted,
  ${BQ_FUNCTION_DATASET}.dlp_freetext_decrypt(${BQ_FUNCTION_DATASET}.dlp_freetext_encrypt(pii_column)) AS dlp_decrypted,
  ${BQ_FUNCTION_DATASET}.aes128ecb_encrypt(pii_column) AS aes_encrypted,
  ${BQ_FUNCTION_DATASET}.aes128ecb_decrypt(fns.aes128ecb_encrypt(pii_column)) AS aes_decrypted,
  ${BQ_FUNCTION_DATASET}.aes128cbc_encrypt(pii_column) AS aes_encrypted,
  ${BQ_FUNCTION_DATASET}.aes128cbc_decrypt(fns.aes128cbc_encrypt(pii_column)) AS aes_decrypted
FROM
  UNNEST(
    [
      'My name is John Doe. My email is john@doe.com', 
      'Some non PII data', 
      '212-233-4532', 
      'some script with simple number 1234']) AS pii_column"

echo "Click the link below for BigQuery Workspace:"
echo -e "https://console.cloud.google.com/bigquery?project=${PROJECT_ID}"
