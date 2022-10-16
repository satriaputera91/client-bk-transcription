package com.bahasakita.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.bahasakita.transcribe.*;
    
public class App {
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
            List<String> Utterances = new ArrayList<String>();

            // add utterance file1
            String UtteranceFile1 = "9b5b76ba-a7b3-4a91-8c72-ca4ba47fb2fa"; 
            Utterances.add(UtteranceFile1);

            // add utterance file2
            String UtteranceFile2 = "e619db34-93d4-4a5f-bfed-1daadacb2c8a";
            Utterances.add(UtteranceFile2);


            BKTranscribe clientBKTranscript = new BKTranscribe(1); // set maximum conncurrent process
            
            clientBKTranscript.setPathFolder("./audio"); 
            clientBKTranscript.process(Utterances); // Add List utterances 

            done.await();
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook);
        }

    }

}
