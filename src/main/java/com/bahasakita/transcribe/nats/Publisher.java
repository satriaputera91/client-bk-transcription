// Copyright 2015-2018 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.bahasakita.transcribe.nats;

import io.nats.streaming.AckHandler;
import io.nats.streaming.NatsStreaming;
import io.nats.streaming.Options;
import io.nats.streaming.StreamingConnection;
import java.io.IOException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

public class Publisher {
    private Topic topic;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final String[] guid = new String[1];
    private boolean async;
    private StreamingConnection sc;

    public Publisher(Topic topic) {
        this.topic = topic;

    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public void sendMessage(String message) {
        byte[] payload = message.getBytes();
        try {
            sendMessage(payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(byte[] message) throws Exception {

        AckHandler acb = new AckHandler() {
            @Override
            public void onAck(String nuid, Exception ex) {
                System.out.printf("Received ACK for guid %s\n", nuid);
                if (ex != null) {
                    System.err.printf("Error in server ack for guid %s: %s", nuid,
                            ex.getMessage());
                }
                if (!guid[0].equals(nuid)) {
                    System.err.printf(
                            "Expected a matching guid in ack callback, got %s vs %s\n", nuid,
                            guid[0]);
                }
                System.out.flush();
                latch.countDown();
            }
        };

        if (!async) {
            try {
                // noinspection ConstantConditions
                sc.publish(topic.getSubject(), message);
            } catch (Exception e) {
                System.err.printf("Error during publish: %s\n", e.getMessage());
                throw (e);
            }
        } else {
            try {
                // noinspection ConstantConditions
                guid[0] = sc.publish(topic.getSubject(), message, acb);
                latch.await();
            } catch (IOException e) {
                System.err.printf("Error during async publish: %s\n", e.getMessage());
                throw (e);
            }

            if (guid[0].isEmpty()) {
                String msg = "Expected non-empty guid to be returned.";
                System.err.println(msg);
                throw new IOException(msg);
            }
            System.out.printf("Published [%s] : [guid: %s]\n", topic.getSubject(),
                    guid[0]);
        }

    }

    public void connect() throws Exception {
        Options opts = NatsStreaming.defaultOptions();
        if (topic.getUrl() != null) {
            opts = new Options.Builder().natsUrl(topic.getUrl()).build();
        }
        try {
            this.sc = NatsStreaming.connect(topic.getClusterId(), topic.getClientId(), opts);
        } catch (Exception e) {
            throw (e);
        }
    }
    
    public void close(){
        try {
            this.sc.close();
        } catch (IOException | TimeoutException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}