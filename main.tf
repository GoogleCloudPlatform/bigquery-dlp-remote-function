#
# Copyright 2023 Google LLC
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

#---

## Define variables

variable "run_code" {
  type = string
  description = "provide a random string for a deployment"
}

variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "zone" {
  type = string
}

variable "artifact_registry_name" {
  type = string
  default = "bq-remote-functions"
}

variable "bq_dataset" {
  type = string
  default = "remote_fns"
}

variable "dlp_deid_template_json_file" {
  type = string
  default = "sample_dlp_deid_config.json"
}

variable "dlp_inspect_template_full_path" {
  default = ""
}

variable "service_name" {
  default = "bq-transform-fns"
}

variable "aes_key_name" {
  default = "bq-transform-fns-aes-key"
}

variable "aes_key_type" {
  default = "BASE64_KEY"
  type    = string
}

variable "aes_cipher_type" {
  default = "AES/CBC/PKCS5PADDING"
  type    = string
}

variable "aes_iv_key_name" {
  default = "bq-transform-fns-iv"
}


######################################
##    Initializing Cloud Services   ##
######################################

terraform {
  required_providers {
    google = {
      source = "hashicorp/google"
      version = "4.74.0"
    }

    google-beta = {
      source = "hashicorp/google-beta"
      version = "4.74.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone = var.zone
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
  zone = var.zone
}

###################################
##    Creating Cloud Resources   ##
###################################


resource "google_service_account" "run_service_account" {
  account_id = "${var.service_name}-runner"
  project    = var.project_id
}

resource "google_secret_manager_secret" "cloud_secret_aes_key" {
  secret_id = var.aes_key_name
  project   = var.project_id
  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret" "cloud_secret_aes_iv_key" {
  secret_id = var.aes_iv_key_name
  project   = var.project_id
  replication {
    automatic = true
  }

}

resource "random_id" "aes_key_random_generator" {
  byte_length = 16

  keepers = {
    run_code = var.run_code
  }
}

resource "random_id" "aes_iv_random_generator" {
  byte_length = 16

  keepers = {
    run_code = var.run_code
  }
}

resource "google_secret_manager_secret_version" "cloud_secret_aes_key_data" {
  secret = google_secret_manager_secret.cloud_secret_aes_key.id

  secret_data = random_id.aes_key_random_generator.b64_std
}

resource "google_secret_manager_secret_version" "cloud_secret_aes_iv_key_data" {
  secret = google_secret_manager_secret.cloud_secret_aes_iv_key.id

  secret_data = random_id.aes_iv_random_generator.b64_std
}

resource "google_secret_manager_secret_iam_binding" "binding_aes_key" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.cloud_secret_aes_key.secret_id
  role      = "roles/secretmanager.secretAccessor"

  members = [
    "serviceAccount:${google_service_account.run_service_account.email}"
  ]
}

resource "google_secret_manager_secret_iam_binding" "binding_iv_key" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.cloud_secret_aes_iv_key.secret_id
  role      = "roles/secretmanager.secretAccessor"

  members = [
    "serviceAccount:${google_service_account.run_service_account.email}"
  ]
}

resource "google_project_iam_binding" "grant_dlp_deidentifyTemplateReader_role" {
  project = var.project_id
  role    = "roles/dlp.deidentifyTemplatesReader"
  members = [
    "serviceAccount:${google_service_account.run_service_account.email}"
  ]
}

resource "google_project_iam_binding" "grant_dlp_user_role" {
  project = var.project_id
  role    = "roles/dlp.user"
  members = [
    "serviceAccount:${google_service_account.run_service_account.email}"
  ]
}

resource "google_artifact_registry_repository" "image_registry" {
  format        = "DOCKER"
  repository_id = var.artifact_registry_name
  project       = var.project_id
  location      = var.region
}


## Create Image using Cloud Build and store in artifact registry
resource "random_id" "build_version" {
  byte_length = 8

  keepers = {
    run_code = var.run_code
  }
}

resource "null_resource" "build_function_image" {
  depends_on = [google_artifact_registry_repository.image_registry]

  triggers = {
    project_id = var.project_id
    region = var.region
    full_image_path = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.image_registry.name}/${var.service_name}:${random_id.build_version.hex}"
  }

  provisioner "local-exec" {
    when    = create
    command = <<EOF
gcloud builds submit \
--project ${var.project_id} \
--region ${var.region} \
--machine-type=e2-highcpu-8 \
--substitutions=_CONTAINER_IMAGE_NAME=${self.triggers.full_image_path}
EOF
  }

  provisioner "local-exec" {
    when    = destroy
    command = <<EOF
gcloud artifacts docker images delete \
${self.triggers.full_image_path} \
--quiet
EOF
  }
}

resource "google_cloud_run_v2_service" "bq_function" {
  location   = var.region
  name       = var.service_name
  project    = var.project_id
  depends_on = [null_resource.build_function_image]

  template {
    containers {
      image = null_resource.build_function_image.triggers.full_image_path
      env {
        name  = "PROJECT_ID"
        value = var.project_id
      }

#      env {
#        name  = "AES_KEY_TYPE"
#        value = var.aes_key_type
#      }
#
#      env {
#        name  = "AES_CIPHER_TYPE"
#        value = var.aes_cipher_type
#      }
#
#
#      env {
#        name = "AES_KEY"
#        value_source {
#          secret_key_ref {
#            secret  = google_secret_manager_secret.cloud_secret_aes_key.secret_id
#            version = "latest"
#          }
#        }
#      }
#
#      env {
#        name = "AES_IV_PARAMETER_BASE64"
#        value_source {
#          secret_key_ref {
#            secret  = google_secret_manager_secret.cloud_secret_aes_iv_key.secret_id
#            version = "latest"
#          }
#        }
#      }
    }
    service_account = google_service_account.run_service_account.email

  }
}

resource "google_bigquery_connection" "external_bq_fn_connection" {
  project       = var.project_id
  connection_id = "ext-${var.service_name}"
  location      = var.region
  description   = "External transformation function connection"
  cloud_resource {}
}

resource "google_project_iam_binding" "grant_bq_connection_run_invoker_role" {
  project = var.project_id
  role    = "roles/run.invoker"
  members = [
    "serviceAccount:${google_bigquery_connection.external_bq_fn_connection.cloud_resource[0].service_account_id}"
  ]
}

resource "google_bigquery_dataset" "routines_dataset" {
  project    = var.project_id
  location   = var.region
  dataset_id = var.bq_dataset
}

## Create DLP DeId Template

resource "random_id" "random_de_id_template_id_random" {
  byte_length = 8
  prefix = "bqdlpfn_"
  keepers = {
    run_code = var.run_code
  }
}

locals {
  template_id = random_id.random_de_id_template_id_random.hex
  de_identify_template_json = merge(jsondecode(file(var.dlp_deid_template_json_file)), {templateId = local.template_id})
}


resource "null_resource" "dlp_de_identify_template" {
  triggers = {
    project_id = var.project_id
    region = var.region
    dlp_de_id_template_id = local.template_id
    dlp_de_id_template_full_path = "projects/${var.project_id}/locations/${var.region}/deidentifyTemplates/${local.template_id}"
  }

  provisioner "local-exec" {
    when    = create
    command = <<EOF
curl -s https://dlp.googleapis.com/v2/projects/${self.triggers.project_id}/locations/${self.triggers.region}/deidentifyTemplates \
--header "X-Goog-User-Project: ${var.project_id}" \
--header "Authorization: Bearer $(gcloud auth print-access-token)" \
--header 'Accept: application/json' \
--header 'Content-Type: application/json' \
--data '${jsonencode(local.de_identify_template_json)}'
EOF

  }

  provisioner "local-exec" {
    when    = destroy
    command = <<EOF
curl -s --request DELETE \
https://dlp.googleapis.com/v2/${self.triggers.dlp_de_id_template_full_path} \
--header "X-Goog-User-Project: ${self.triggers.project_id}" \
--header "Authorization: Bearer $(gcloud auth print-access-token)" \
--header 'Accept: application/json' \
--header "Content-Type: application/json"
EOF

  }
}

## Create BigQuery remote functions
resource "random_id" "bq_job_random" {
  byte_length = 8
}

resource "null_resource" "bq_dlp_encrypt_function" {
  depends_on = [null_resource.dlp_de_identify_template, google_cloud_run_v2_service.bq_function, google_bigquery_connection.external_bq_fn_connection, google_bigquery_dataset.routines_dataset]

  triggers = {
    project_id = var.project_id
    region = var.region
    dataset_id = var.bq_dataset
    cloud_service_name = google_cloud_run_v2_service.bq_function.id
    cloud_run_uri = google_cloud_run_v2_service.bq_function.uri
  }

  provisioner "local-exec" {
    when = create
    command = <<EOF
bq query --project_id "${self.triggers.project_id}" \
--use_legacy_sql=false \
"CREATE OR REPLACE FUNCTION ${self.triggers.dataset_id}.dlp_freetext_encrypt(v STRING) RETURNS STRING \
REMOTE WITH CONNECTION \`${self.triggers.project_id}.${self.triggers.region}.${google_bigquery_connection.external_bq_fn_connection.connection_id}\` \
OPTIONS (endpoint = '${self.triggers.cloud_run_uri}', user_defined_context = [('mode', 'deidentify'),('algo','dlp'),('dlp-deid-template','${null_resource.dlp_de_identify_template.triggers.dlp_de_id_template_full_path}'),('dlp-inspect-template','${var.dlp_inspect_template_full_path}')]);" \
EOF
  }

  provisioner "local-exec" {
    when = destroy
    command = <<EOF
bq query --project_id "${self.triggers.project_id}" \
--use_legacy_sql=false \
"DROP FUNCTION ${self.triggers.dataset_id}.dlp_freetext_encrypt" \
EOF
  }
}


resource "null_resource" "bq_dlp_decrypt_function" {

  depends_on = [null_resource.dlp_de_identify_template, google_cloud_run_v2_service.bq_function, google_bigquery_connection.external_bq_fn_connection, google_bigquery_dataset.routines_dataset]

  triggers = {
    project_id = var.project_id
    region = var.region
    dataset_id = var.bq_dataset
    cloud_service_name = google_cloud_run_v2_service.bq_function.id
    cloud_run_uri = google_cloud_run_v2_service.bq_function.uri
  }

  provisioner "local-exec" {
    when = create
    command = <<EOF
bq query --project_id "${self.triggers.project_id}" \
--use_legacy_sql=false \
"CREATE OR REPLACE FUNCTION ${self.triggers.dataset_id}.dlp_freetext_decrypt(v STRING) RETURNS STRING \
REMOTE WITH CONNECTION \`${self.triggers.project_id}.${self.triggers.region}.${google_bigquery_connection.external_bq_fn_connection.connection_id}\` \
OPTIONS (endpoint = '${self.triggers.cloud_run_uri}', user_defined_context = [('mode', 'reidentify'),('algo','dlp'),('dlp-deid-template','${null_resource.dlp_de_identify_template.triggers.dlp_de_id_template_full_path}'),('dlp-inspect-template','${var.dlp_inspect_template_full_path}')]);" \
EOF
  }

  provisioner "local-exec" {
    when = destroy
    command = <<EOF
bq query --project_id "${self.triggers.project_id}" \
--use_legacy_sql=false \
"DROP FUNCTION ${self.triggers.dataset_id}.dlp_freetext_decrypt" \
EOF
  }
}
