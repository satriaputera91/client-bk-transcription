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

package com.bahasakita.transcribe;

import java.util.concurrent.CountDownLatch;

import com.bahasakita.transcribe.nats.InterfaceSubscriber;
import com.bahasakita.transcribe.nats.Subscriber;
import com.bahasakita.transcribe.nats.Topic;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TranscribeSubscriber implements InterfaceSubscriber {
    private String finalTranscript;
    private boolean bEOS = false;
    private Subscriber subsciber;
    private ResponseHandler handler;
    private final CountDownLatch start = new CountDownLatch(1);

    public TranscribeSubscriber(Topic topic, ResponseHandler handler) {
        this.subsciber = new Subscriber(topic);
        this.subsciber.setSubscriberHandler(this);

        try {
            this.subsciber.run();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.handler = handler;
    }

    public String getFinalTrancript() {
        return finalTranscript;
    }

    public boolean statusEOS() {
        return bEOS;
    }

    public void stop() {
        this.subsciber.close();
    }

    public void await() {
        try {
            this.start.await();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void getMessage(String message) {
        // TODO Auto-generated method stub
        // System.out.println("Response Message :"+message);
        JsonObject obj = new JsonParser().parse(message).getAsJsonObject();

        if (obj.get("state").getAsString().equals("FINAL")) {
            bEOS = true;
            finalTranscript = obj.getAsJsonObject("message").get("text").getAsString();
            this.start.countDown();
            // System.out.println("FINAL :" + finalTranscript);

        } else if (obj.get("state").getAsString().equals("PARTIAL")) {
            System.out.println("PARTIAL :" + obj.getAsJsonObject("message").get("text").getAsString());

        } else if (obj.get("state").getAsString().equals("DECODER_END") && !finalTranscript.isEmpty()) {
            // System.out.println("EOS");
            this.start.countDown();
            bEOS = true;
        }
        // System.out.println("FINAL :"+finalTranscript);

        this.handler.StatusEOSTranscript(bEOS);

    }

}