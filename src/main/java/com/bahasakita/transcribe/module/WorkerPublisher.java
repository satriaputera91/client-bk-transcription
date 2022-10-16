package com.bahasakita.transcribe.module;

import java.util.logging.Logger;

import com.bahasakita.transcribe.nats.Publisher;
import com.bahasakita.transcribe.nats.Topic;
import com.google.gson.JsonObject;

public class WorkerPublisher {
    private Logger logger = Logger.getLogger(WorkerPublisher.class.getName());

    private Topic topic;
    private Publisher publish;
    private boolean async = false;

    public WorkerPublisher(Topic topic) {
        this.topic = topic;
        this.publish = new Publisher(this.topic);
        this.publish.setAsync(async);
        try {
            this.publish.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // System.out.println(String.format("Publisher Topic, URL :%s, Subject: %s,
        // Cluster-Id: %s,Client-Id: %s",
        // this.topic.getUrl(),
        // topic.getSubject(), topic.getClusterId(), topic.getClientId()));
        
        logger.info(String.format("Publisher Topic, URL :%s, Subject: %s, Cluster-Id: %s,Client-Id: %s",
                this.topic.getUrl(),
                topic.getSubject(), topic.getClusterId(), topic.getClientId()));

    }

    public void sendWorker(String utteranceID, String resultTopic, String responseTopic) {
        JsonObject payload = new JsonObject();

        payload.addProperty("client_id", utteranceID);
        payload.addProperty("result_topic", resultTopic);
        payload.addProperty("response_topic", responseTopic);

        String message = payload.toString();
        this.publish.sendMessage(message);
        this.publish.close();
    }

}
