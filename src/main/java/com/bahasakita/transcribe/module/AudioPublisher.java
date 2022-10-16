package com.bahasakita.transcribe.module;

import java.util.logging.Logger;

import com.bahasakita.transcribe.nats.Publisher;
import com.bahasakita.transcribe.nats.Topic;

public class AudioPublisher {
    private Logger logger = Logger.getLogger(AudioPublisher.class.getName());

    private Topic topic;
    private Publisher publish;
    private final boolean async = false;
    private final int maxSizeAudio = 6400;

    public AudioPublisher(Topic topic) {
        this.topic = topic;
        this.publish = new Publisher(this.topic);
        this.publish.setAsync(async);
        try {
            this.publish.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // System.out.println(String.format("Publisher Topic, URL :%s, Subject: %s, Cluster-Id: %s,Client-Id: %s",
        //         this.topic.getUrl(),
        //         topic.getSubject(), topic.getClusterId(), topic.getClientId()));
         logger.info(String.format("AudioPublisher Topic, URL :%s, Subject: %s, Cluster-Id: %s, Client-Id: %s",
                this.topic.getUrl(),
                topic.getSubject(), topic.getClusterId(), topic.getClientId()));
    }

    public int getMaxSizeAudio() {
        return maxSizeAudio;
    }

    //

    public void sendAudio(byte[] bytes) throws Exception {
        this.publish.sendMessage(bytes);

    }

    public void sendEOS() {

        char eos = '0';
        byte[] bEOS = new byte[1];
        bEOS[0] = (byte) eos;

        try {
            this.publish.sendMessage(bEOS);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.publish.close();

    }

    // public void sendAudio(String filename) throws Exception {
    // AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new
    // File(filename));
    // int bytesPerFrame = audioInputStream.getFormat().getFrameSize();

    // if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) {
    // // some audio formats may have unspecified frame size
    // // in that case we may read any amount of bytes
    // bytesPerFrame = 1;
    // }

    // System.out.println(audioInputStream.getFormat().toString());

    // float sampleRate = audioInputStream.getFormat().getSampleRate();

    // byte[] bytes = new byte[maxSizeAudio];
    // int channels = audioInputStream.getFormat().getChannels();

    // if (sampleRate == 16000.0f && bytesPerFrame >= 2 && channels == 1) {
    // while (audioInputStream.read(bytes) != -1) {
    // this.publish.sendMessage(bytes);
    // }
    // }
    // audioInputStream.close();

    // char eos = '0';
    // byte[] bEOS = new byte[1];
    // bEOS[0] = (byte) eos;
    // this.publish.sendMessage(bEOS);
    // this.publish.close();

    // }

}
