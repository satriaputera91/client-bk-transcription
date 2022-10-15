package com.bahasakita.transcribe.nats;

public class Topic {

    private String url;
    private String subject;
    private String clusterId = "test-cluster";
    private String clientId = "test-client";

    public Topic(String url, String clusterId, String clientId, String subject) {
        this.url = url;
        this.clusterId = clusterId;
        this.clientId = clientId;
        this.subject = subject;
    }

    public String getUrl() {
        return url;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSubject() {
        return subject;
    }

    public String getClusterId() {
        return clusterId;
    }
}
