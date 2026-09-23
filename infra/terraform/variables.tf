variable "aws_region" {
  description = "LocalStack region (match AWS_REGION in the root .env)."
  type        = string
  default     = "us-east-1"
}

variable "observation_queue_name" {
  description = "Local observation queue (match OBSERVATION_QUEUE_NAME in the root .env)."
  type        = string
  default     = "observation-processing"
}

variable "localstack_endpoint" {
  description = "LocalStack SQS endpoint on the host (match LOCALSTACK_PORT if overridden)."
  type        = string
  default     = "http://localhost:4566"

  validation {
    condition     = can(regex("^http://(localhost|127\\.0\\.0\\.1):[0-9]+$", var.localstack_endpoint))
    error_message = "Use a local HTTP endpoint, such as http://localhost:4566."
  }
}

variable "visibility_timeout_seconds" {
  description = "Time before an unacknowledged message can be received again."
  type        = number
  default     = 30

  validation {
    condition     = var.visibility_timeout_seconds >= 1 && var.visibility_timeout_seconds <= 43200 && floor(var.visibility_timeout_seconds) == var.visibility_timeout_seconds
    error_message = "Visibility timeout must be an integer from 1 to 43200 seconds."
  }
}

variable "max_receive_count" {
  description = "Number of unsuccessful receives before moving a message to the DLQ."
  type        = number
  default     = 5

  validation {
    condition     = var.max_receive_count >= 1 && var.max_receive_count <= 1000 && floor(var.max_receive_count) == var.max_receive_count
    error_message = "Maximum receive count must be an integer from 1 to 1000."
  }
}
