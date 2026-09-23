# Local development only. Compose runs LocalStack; Terraform owns its queues.
provider "aws" {
  region                      = var.aws_region
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    sqs = var.localstack_endpoint
  }
}

resource "aws_sqs_queue" "observation_processing_dlq" {
  name                      = "${var.observation_queue_name}-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "observation_processing" {
  name                       = var.observation_queue_name
  visibility_timeout_seconds = var.visibility_timeout_seconds
  receive_wait_time_seconds  = 20
  message_retention_seconds  = 345600
}

resource "aws_sqs_queue_redrive_policy" "observation_processing" {
  queue_url = aws_sqs_queue.observation_processing.id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.observation_processing_dlq.arn
    maxReceiveCount     = var.max_receive_count
  })
}
