package com.example.wgo.messaging;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsMessageReceiver {

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;

    public <T> ReceivedMessages<T> receive(String queueName, Class<T> messageType) {
        String queueUrl = sqs.getQueueUrl(
                        GetQueueUrlRequest.builder().queueName(queueName).build())
                .queueUrl();
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20)
                        .build())
                .messages();
        log.debug("Received {} messages from queue={}", messages.size(), queueName);
        List<ReceivedMessage<T>> receivedMessages = messages.stream()
                .map(message -> new ReceivedMessage<>(message, objectMapper.readValue(message.body(), messageType)))
                .toList();
        return new ReceivedMessages<>(queueUrl, receivedMessages);
    }

    public record ReceivedMessages<T>(String queueUrl, List<ReceivedMessage<T>> messages) {}

    public record ReceivedMessage<T>(Message message, T payload) {}
}
