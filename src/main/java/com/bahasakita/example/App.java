package com.bahasakita.example;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import com.bahasakita.transcribe.*;
import com.bahasakita.transcribe.nats.Topic;

public class App {

    private static final String URL = "nats://bhskita.dynu.com:4211";
    private static final String MAIN_CHANNEL = "api.gateway.";
    private static final String ENV_CHANNEL = "dev.";
    private static final String CLUSTER_ID = "test-cluster";
    private static final String CHANNEL_RESPONSE_CONNECTION = MAIN_CHANNEL + ENV_CHANNEL + "worker.response.";
    private static final String CHANNEL_RESULT_TRANSCRIPT = MAIN_CHANNEL + ENV_CHANNEL + "worker.result.";
    private static final String CHANNEL_WORKER_REQUEST = MAIN_CHANNEL + ENV_CHANNEL + "worker.connect";
    private static final String CHANNEL_AUDIO_PUBLISHER = MAIN_CHANNEL + ENV_CHANNEL + "worker.audio.";

    private static ResponseHandler response = new ResponseHandler() {

        @Override
        public void StatusEOSTranscript(Boolean status) {
            if (status) {
                System.out.println("Status EOS");
            }
            // System.out.println("Status Transcripting");

        }

        @Override
        public void StatusWorker(String status) {

            System.out.println("Status worker :" + status);
        }

    };

    public static void transcriptProcess(String Filename) {

        String utteranceID = UUID.randomUUID().toString();
        String transcriptResponseTopic = CHANNEL_RESULT_TRANSCRIPT + utteranceID;
        String workerResponseTopic = CHANNEL_RESPONSE_CONNECTION + utteranceID;
        String sendAudioTopic = CHANNEL_AUDIO_PUBLISHER + utteranceID;

        try {

            WorkerSubscriber worker = workerResponse(workerResponseTopic, response);
            TranscribeSubscriber transcript = resultTranscript(transcriptResponseTopic, response);

            workerRequest(CHANNEL_WORKER_REQUEST, utteranceID, transcriptResponseTopic, workerResponseTopic);

            worker.await();
            System.out.println("Check Status :" + worker.checkStatus());

            if (worker.checkStatus()) {

                // String Filename = "./audio/mono.wav";
                System.out.println("Send Audio :" + Filename);

                sendAudio(sendAudioTopic, Filename);

                transcript.await();

                String finalResult = transcript.getFinalTrancript();
                System.out.println("Result FINAL :" + finalResult);

            }
            worker.stop();
            transcript.stop();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static void workerRequest(String topic, String uteranceID, String transcriptResponse,
            String workerResponse) {
        String CLIENT_ID = "sample_request_worker";

        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        WorkerPublisher worker = new WorkerPublisher(myTopic);
        worker.sendWorker(uteranceID, transcriptResponse, workerResponse);

    }

    public static WorkerSubscriber workerResponse(String topic, ResponseHandler response) {
        String CLIENT_ID = "sample_response_worker";
        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        return new WorkerSubscriber(myTopic, response);
    }

    public static TranscribeSubscriber resultTranscript(String topic, ResponseHandler response) {
        String CLIENT_ID = "sample_response_transcript";
        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        return new TranscribeSubscriber(myTopic, response);

    }

    public static void sendAudio(String topic, String filename) {
        String CLIENT_ID = "sample_request_audio";

        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        AudioPublisher worker = new AudioPublisher(myTopic);

        try {
            worker.sendAudio(filename);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static void main(String[] args) throws Exception {
        Thread hook = null;
        CountDownLatch done = new CountDownLatch(1);

        try {

            hook = new Thread() {
                public void run() {
                    System.err.println("\nCaught CTRL-C, shutting down gracefully...\n");
                    done.countDown();
                }
            };

            Runtime.getRuntime().addShutdownHook(hook);

            int i = 0;
            String Filename = "./audio/FS_001_10DTK.wav";
            while (i < 10) {

                transcriptProcess(Filename);
                i++;

            }
            done.await();
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook);
        }

    }

}
