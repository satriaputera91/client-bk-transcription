package com.bahasakita.transcribe;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.bahasakita.transcribe.module.AudioPublisher;
import com.bahasakita.transcribe.module.TranscribeSubscriber;
import com.bahasakita.transcribe.module.WorkerPublisher;
import com.bahasakita.transcribe.module.WorkerSubscriber;
import com.bahasakita.transcribe.nats.Topic;

public class BKTranscribe {
    private Logger logger = Logger.getLogger(BKTranscribe.class.getName());

    private String URL = "nats://bhskita.dynu.com:4211";
    private String MAIN_CHANNEL = "api.gateway.";
    private String ENV_CHANNEL = "dev.";
    private String CLUSTER_ID = "test-cluster";
    private String CHANNEL_RESPONSE_CONNECTION;
    private String CHANNEL_RESULT_TRANSCRIPT = MAIN_CHANNEL + ENV_CHANNEL + "worker.result.";
    private String CHANNEL_WORKER_REQUEST = MAIN_CHANNEL + ENV_CHANNEL + "worker.connect";
    private String CHANNEL_AUDIO_PUBLISHER = MAIN_CHANNEL + ENV_CHANNEL + "worker.audio.";
    private String PATH_FOLDER = "./audio";
    private int MAX_WORKERS = 1;

    public BKTranscribe(Integer maxWorkers) {

        this.MAX_WORKERS = maxWorkers;
        CHANNEL_RESPONSE_CONNECTION = MAIN_CHANNEL + ENV_CHANNEL + "worker.response.";
        CHANNEL_RESULT_TRANSCRIPT = MAIN_CHANNEL + ENV_CHANNEL + "worker.result.";
        CHANNEL_AUDIO_PUBLISHER = MAIN_CHANNEL + ENV_CHANNEL + "worker.audio.";
    }

    public BKTranscribe(String URL, Integer maxWorkers, String mainChannel, String environment, String ClusterId) {
        this.URL = URL;
        this.MAIN_CHANNEL = mainChannel;
        this.ENV_CHANNEL = environment;
        this.CLUSTER_ID = ClusterId;
        this.MAX_WORKERS = maxWorkers;

        CHANNEL_RESPONSE_CONNECTION = MAIN_CHANNEL + ENV_CHANNEL + "worker.response.";
        CHANNEL_RESULT_TRANSCRIPT = MAIN_CHANNEL + ENV_CHANNEL + "worker.result.";
        CHANNEL_AUDIO_PUBLISHER = MAIN_CHANNEL + ENV_CHANNEL + "worker.audio.";
    }

    public void setPathFolder(String pathFolder) {
        this.PATH_FOLDER = pathFolder;
    }

    public void process(List<String> Utterances) {

        while (!Utterances.isEmpty()) {

            if (Utterances.size() < MAX_WORKERS) {
                MAX_WORKERS = Utterances.size();
            }

            List<WorkerSubscriber> workers = new ArrayList<WorkerSubscriber>();

            List<TranscribeSubscriber> transcripts = new ArrayList<TranscribeSubscriber>();
            List<String> audioTopics = new ArrayList<String>();

            for (int i = 0; i < MAX_WORKERS; i++) {

                try {

                    String utteranceID = Utterances.get(i);
                    String transcriptResponseTopic = CHANNEL_RESULT_TRANSCRIPT + utteranceID;
                    String workerResponseTopic = CHANNEL_RESPONSE_CONNECTION + utteranceID;
                    String sendAudioTopic = CHANNEL_AUDIO_PUBLISHER + utteranceID;

                    audioTopics.add(i, sendAudioTopic);
                    WorkerSubscriber worker = workerResponse(workerResponseTopic);
                    workers.add(i, worker);

                    TranscribeSubscriber transcript = resultTranscript(transcriptResponseTopic);
                    transcripts.add(i, transcript);
                    workerRequest(CHANNEL_WORKER_REQUEST, utteranceID, transcriptResponseTopic, workerResponseTopic);

                    workers.get(i).await();
                    //System.out.println("Check Status :" + workers.get(i).checkStatus());
                    logger.info("Check Status :" + workers.get(i).checkStatus());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            for (int i = 0; i < MAX_WORKERS; i++) {
                try {

                    if (workers.get(i).checkStatus()) {

                        sendAudioFromFile(audioTopics.get(i), Utterances.remove(i)); // change with object storage
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }

            for (int i = 0; i < MAX_WORKERS; i++) {

                transcripts.get(i).await();
                String finalResult = transcripts.get(i).getFinalTrancript();
                //System.out.println("Result FINAL :" + finalResult);
                logger.info("Result FINAL :" + finalResult);
                
                workers.get(i).stop();
                transcripts.get(i).stop();
            }
        }
    }

    public void workerRequest(String topic, String uteranceID, String transcriptResponse,
            String workerResponse) {
        String CLIENT_ID = UUID.randomUUID().toString();

        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        WorkerPublisher worker = new WorkerPublisher(myTopic);
        worker.sendWorker(uteranceID, transcriptResponse, workerResponse);

    }

    public WorkerSubscriber workerResponse(String topic) {
        String CLIENT_ID = UUID.randomUUID().toString();
        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        return new WorkerSubscriber(myTopic);
    }

    public TranscribeSubscriber resultTranscript(String topic) {
        String CLIENT_ID = UUID.randomUUID().toString();
        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        return new TranscribeSubscriber(myTopic);

    }

    public void sendAudioFromObjectStorage(String topic, String filename) {
        // logic process with object storage

    }

    public void sendAudioFromFile(String topic, String filename) {

        // String Filename = "./audio/mono.wav";
        filename = Paths.get(this.PATH_FOLDER, filename + ".wav").toString();

        //System.out.println("Send Audio :" + filename);
        logger.info("Send Audio :" + filename);
        
        String CLIENT_ID = UUID.randomUUID().toString();

        Topic myTopic = new Topic(URL, CLUSTER_ID, CLIENT_ID, topic);
        AudioPublisher worker = new AudioPublisher(myTopic);

        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(filename));
            int bytesPerFrame = audioInputStream.getFormat().getFrameSize();

            if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) {
                bytesPerFrame = 1;
            }

            //System.out.println(audioInputStream.getFormat().toString());
            logger.info(audioInputStream.getFormat().toString());

            float sampleRate = audioInputStream.getFormat().getSampleRate();

            byte[] bytes = new byte[worker.getMaxSizeAudio()];
            int channels = audioInputStream.getFormat().getChannels();

            if (sampleRate == 16000.0f && bytesPerFrame >= 2 && channels == 1) {
                while (audioInputStream.read(bytes) != -1) {
                    worker.sendAudio(bytes);
                }
            }
            worker.sendEOS();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
