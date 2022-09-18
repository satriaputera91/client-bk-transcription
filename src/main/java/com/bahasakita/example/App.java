package com.bahasakita.example;

import com.bahasakita.transcription.*;

public class App {
    public static void main(String[] args) throws Exception {
        try {
            new Subscriber(args).run();
        } catch (IllegalArgumentException e) {
            System.out.flush();
            System.err.println(e.getMessage());
            Subscriber.usage();
            System.err.flush();
            throw e;
        }
    }
}
