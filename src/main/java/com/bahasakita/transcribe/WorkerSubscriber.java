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

public class WorkerSubscriber implements InterfaceSubscriber {
    private boolean bStatus = false;
    private Subscriber subsciber;
    private ResponseHandler handler ;
    private final CountDownLatch start = new CountDownLatch(1);

    public WorkerSubscriber(Topic topic,ResponseHandler handler) {
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

    public boolean checkStatus() {
        return bStatus;
    }

    public void stop(){
        
        this.subsciber.close();
    }
    public void await(){
        try {
            this.start.await();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void getMessage(String message) {
        JsonObject obj = new JsonParser().parse(message).getAsJsonObject();
        String status;
        if (obj.get("status").getAsBoolean() == true) {
            bStatus = true;
            status = "Success";
        } else {
            status = "Failed";
            bStatus = false;
        }
        this.start.countDown();
        this.handler.StatusWorker(status);
    }

}