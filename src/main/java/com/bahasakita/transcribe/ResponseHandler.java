package com.bahasakita.transcribe;

public interface ResponseHandler {
 
    public void StatusEOSTranscript(Boolean status);
    
    public void StatusWorker(String status);
    
}
