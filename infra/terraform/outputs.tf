output "observation_processing_queue_url" {
  value       = aws_sqs_queue.observation_processing.url
  description = "Processing queue URL."
}

output "observation_processing_dlq_url" {
  value       = aws_sqs_queue.observation_processing_dlq.url
  description = "Dead-letter queue URL."
}

output "observation_processing_dlq_arn" {
  value       = aws_sqs_queue.observation_processing_dlq.arn
  description = "Dead-letter queue ARN used by the redrive policy."
}
