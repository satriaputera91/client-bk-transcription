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

import io.nats.streaming.Message;
import io.nats.streaming.MessageHandler;
import io.nats.streaming.NatsStreaming;
import io.nats.streaming.Options;
import io.nats.streaming.StreamingConnection;
import io.nats.streaming.Subscription;
import io.nats.streaming.SubscriptionOptions;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class Subscriber {
    private Logger logger = Logger.getLogger(Subscriber.class.getName());
    private Topic topic;
    private final SubscriptionOptions.Builder builder = new SubscriptionOptions.Builder();
    private String qgroup;
    private String durable;
    private boolean unsubscribe;
    private final CountDownLatch start = new CountDownLatch(1);
    private StreamingConnection sc;
    private Subscription sub;
    private InterfaceSubscriber subscriberHandler;

    public Subscriber(Topic topic) {
        this.topic = topic;
    }

    public void setSubscriberHandler(InterfaceSubscriber subscriberHandler) {
        this.subscriberHandler = subscriberHandler;
    }

    public void run() throws Exception {
        Options opts = null;
        if (topic.getUrl() != null) {
            opts = new Options.Builder().natsUrl(topic.getUrl()).build();
        }

        this.sc = NatsStreaming.connect(topic.getClusterId(), topic.getClientId(), opts);

        try {
            this.sub = sc.subscribe(topic.getSubject(), qgroup, new MessageHandler() {
                public void onMessage(Message msg) {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        /* NOOP */
                    }
                    String data = new String(msg.getData(), StandardCharsets.UTF_8);
                    // System.out.printf("[#%d] Received on [%s]: '%s'\n",
                    // delivered.incrementAndGet(), msg.getSubject(),data);

                    subscriberHandler.getMessage(data);
                }
            }, builder.build());

            // System.out.printf("Listening on [%s], clientID=[%s], qgroup=[%s]
            // durable=[%s]\n",
            // sub.getSubject(), topic.getClientId(), sub.getQueue(),
            // sub.getOptions().getDurableName());

            logger.info(String.format("Listening on [%s], clientID=[%s], qgroup=[%s] durable=[%s]",
                    sub.getSubject(), topic.getClientId(), sub.getQueue(),
                    sub.getOptions().getDurableName()));

            start.countDown();

        } catch (Exception e) {
            throw (e);
        }
    }

    public void close() {
        try {
            if (durable == null || durable.isEmpty() || unsubscribe) {
                this.sub.unsubscribe();
            }
            this.sc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}