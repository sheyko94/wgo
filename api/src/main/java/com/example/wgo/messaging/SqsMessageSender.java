package com.example.wgo.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsMessageSender {

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;

    public void send(String queueName, Object message) {
        log.debug("Sending message to queue={}", queueName);
        String queueUrl = sqs.getQueueUrl(
                        GetQueueUrlRequest.builder().queueName(queueName).build())
                .queueUrl();
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(objectMapper.writeValueAsString(message))
                .build());
        log.info("Sent message to queue={}", queueName);
    }
}
