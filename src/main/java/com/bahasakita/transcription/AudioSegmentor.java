/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bahasakita.segmentor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioSegmentor  {

    // konstanta Audio Format

    static final int MAX_BUFFER = 512;
    
    // Konstanta Segmentation
    final private double T_REF = 5e-3;
    final private double DB_REF = 5.0;
    final private int SILENT_PADDING = 40; // leght of padding
    final private int MAX_ITER = 20; // number of iterator for training
    final private float DURATION = 10.0f;

    private int STOP_RUNNING_SILENT = 16;
    // private float KONSTANTA_TRESHOLD = 0.71f;
    private float KONSTANTA_TRESHOLD = 0.32f;
    private float CHUNK_TRESHOLD;

    // Member Data Audio Segmentor
    private int SILENT_PADDING_COUNTER; // leght of padding counter
    private int SILENT_COUNTER; // leght of padding counter
    private boolean ACTIVE_SILENT_COUNTER; // leght of padding counter
    private AudioSegmentorHandler handler;
    private AudioFormat audioFormat;
    private ByteArrayOutputStream baos;

    private boolean isPause;
    private boolean isChange;

    // private boolean isMute = false;
    private double mu_1 = 10; // initial mean of unvoiced data
    private double mu_2 = 90; // initial mean of voiced data
    
    private double MINIMUM_TRESHOLD = 50;
    private double SILENT_TRESHOLD;
    private boolean isFirst;
    private ArrayList<Double> dBVector;
   
    private ArrayList<Integer> dBVectorFlag;
    private ArrayList<Byte[]> dBVectorData;
    private int countTraining;
    private int countSegmentation;
    private int RUNNING_SILENT = SILENT_PADDING;
    private static double monitor_silent_sum = 0;
    private static int monitor_silent_count = 0;
    private static double silent_stddev = 0;
    private static double monitor_sound_sum = 0;
    private static int monitor_sound_count = 0;
    private static double sound_stddev = 0;
    private static int mic_change_count = 0;
    private static boolean mic_change_state = false;
    private int population_limit = 125;
    private double sigmaBound = 2.5;
    private int changeBound = 5;
    private int channel ;
    ////////////////////////////////////////////////////////////////////////////////

    public AudioSegmentor(AudioSegmentorHandler handler) {
       this.handler = handler;
    }

    
    public void properties(AudioFormat audioFormat)
    {
        this.audioFormat = audioFormat;
        this.CHUNK_TRESHOLD = (this.audioFormat.getSampleRate() * this.audioFormat.getSampleSizeInBits() / 8)* DURATION;
    
        this.isFirst = true;
        this.baos = new ByteArrayOutputStream();
      
        this.dBVectorFlag = new ArrayList<>();
        this.dBVectorData = new ArrayList<Byte[]>();
        this.dBVector = new ArrayList<Double>();
        this.population_limit = 125;
        this.sigmaBound = 2.5;
        this.changeBound = 5;
        
        this.countTraining = 0;
        this.SILENT_PADDING_COUNTER = 0;

        // this.isTraining = true;
        this.SILENT_COUNTER = 0;
        this.ACTIVE_SILENT_COUNTER = true;

        this.countTraining = 0;

        this.isChange = false;
                
    }

    public void set_channel(int channel)
    {
        this.channel = channel; 
    }

    public void set_training(boolean training) {

        this.countTraining = 0;
        this.isFirst = true;
    }   

    public double powerLevel(byte data[]) {
        double dB;

        double rootMeanSquare = 0.0;
        for (int i = 0; i < data.length; i = i + 2) {
            short s = (short) (data[i] + (short) (data[i + 1] << 8));

            rootMeanSquare += (s * s);
        }

        rootMeanSquare = Math.sqrt(rootMeanSquare / data.length);
        dB = 10 * Math.log(Math.pow(rootMeanSquare / T_REF, 2)) + DB_REF;

        if (Double.isInfinite(dB)) {
            dB = SILENT_TRESHOLD;
        }

        return dB;
    }

    public static short[] shortMe(byte[] bytes) {
        short[] out = new short[bytes.length / 2]; // will drop last byte if odd number
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        for (int i = 0; i < out.length; i++) {
            out[i] = bb.getShort();
        }
        return out;
    }

    public double powerLevelDB(byte data[]) {
        double dB;

        double d;
        short s;
        double rootMeanSquare = 0.0;
        for (int i = 0; i < data.length; i = i + 2) {
            s = (short) (data[i] + (short) (data[i + 1] << 8));
            d = (double) s;

            if (Math.abs(d) == 0) {
                d = 1e-12;
            }
            rootMeanSquare += (d * d);
        }

        rootMeanSquare = Math.sqrt(rootMeanSquare / (data.length / 2));
        dB = 20 * Math.log10(rootMeanSquare);

        if (Double.isInfinite(dB)) {
            // dB = SILENT_TRESHOLD;
            dB = 1e-12;
            System.out.println("infinte db");
        }

        return dB;
    }

    public void segmentation(byte data[]) {

        double dB = powerLevelDB(data);

        // kondisi pengecekan volume suara untuk pelatihan penyesuaian threshold
        if (dB > 0) {

            if (isFirst || isChange) {
                dBVector.add(dB);
                countTraining++;
                resetDetection();
                if (countTraining == (((int) (this.audioFormat.getSampleRate() * this.audioFormat.getSampleSizeInBits() / 8) / MAX_BUFFER) * 3)) {
                    //System.out.println("------ Training --------");
                    training(dBVector);
                }

            }
            /*
             * else if (isTraining){ && countTraining == (SAMPlE_BYTES * 5)) {
             * System.out.println("------ Second Training --------");
             * training(dBVector);
             * 
             * else if (!isTraining && countTraining >= (SAMPlE_BYTES * 5)) {
             * dBVector.remove(0);
             * countTraining--;
             * }
             */

        }

        /*
         * if (isFirst || isChange) {
         * resetDetection();
         * }
         */
        if (dB <= SILENT_TRESHOLD) {
            // Protect from variable overloading
            if (Integer.MAX_VALUE < monitor_silent_count + 1
                    || Double.MAX_VALUE < monitor_silent_sum + dB) {
                monitor_silent_sum /= monitor_silent_count;
                monitor_silent_count = 1;
            }
            // Restrict the moving average population
            if (monitor_silent_count + 1 > population_limit) {
                monitor_silent_sum /= monitor_silent_count;
                monitor_silent_count = 1;
            }
            monitor_silent_sum += dB;
            monitor_silent_count++;
        } else {
            // Protect from variable overloading
            if (Integer.MAX_VALUE < monitor_sound_count + 1
                    || Double.MAX_VALUE < monitor_sound_sum + dB) {
                monitor_sound_sum /= monitor_sound_count;
                monitor_sound_count = 1;
            }
            // Restrict the moving average population
            if (monitor_sound_count + 1 > population_limit) {
                monitor_sound_sum /= monitor_sound_count;
                monitor_sound_count = 1;
            }
            monitor_sound_sum += dB;
            monitor_sound_count++;
        }
        double silent_db = monitor_silent_sum / monitor_silent_count;
        double silent_change_uplimit = mu_1 + (sigmaBound * silent_stddev);
        double silent_change_lowlimit = mu_1 - (sigmaBound * silent_stddev);
        double sound_db = monitor_sound_sum / monitor_sound_count;
        double sound_change_uplimit = mu_2 + (sigmaBound * sound_stddev);
        double sound_change_lowlimit = mu_2 - (sigmaBound * sound_stddev);

        if (!isChange | !isFirst) {
            // melakukan pengecekan terhadap kondisi suara yang masuk
            // apakah terjadi perubahan volume suara atu tidak
            if (Double.isNaN(silent_db) || Double.isNaN(sound_db)) {
                mic_change_count = 0;
            } else if (silent_change_lowlimit > silent_db
                    || silent_change_uplimit < silent_db
                    || sound_change_lowlimit > sound_db
                    || sound_change_uplimit < sound_db) {

                // terdeteksi terjadi perubahan suara
                mic_change_count++;
                if (dB > 0) {
                    dBVectorFlag.add(1);
                    dBVectorData.add(toObjects(data));
                }
                if (mic_change_count > changeBound
                        && mic_change_state == false) {
                    // ada perubahan suara
                    mic_change_state = true;
                    mic_change_count = 0;
                }
            } else {
                // tidak terjadi perubahan suara
                mic_change_count = 0;
            }

            //if (mic_change_state == true) {
                // System.out.printf(
                //         " Monitor : dB : (%.2f/%.2f) \tmu_1: |%.2f <= (%.2f/%.2f) => %.2f| \tmu2:|%.2f <= (%.2f/%.2f)=> %.2f|, isChange:%s, isFirst:%s\n",
                //         dB, SILENT_TRESHOLD, silent_change_lowlimit, silent_db, mu_1, silent_change_uplimit,
                //         sound_change_lowlimit, sound_db, mu_2, sound_change_uplimit,
                //         Boolean.toString(isChange), Boolean.toString(isFirst));
                // System.out.printf(
                //         "(Mic Change Detection)\n");

            //} else {
                // System.out.printf(
                //         " Monitor : dB : (%.2f/%.2f) \tmu_1: |%.2f <= (%.2f/%.2f) => %.2f| \tmu2:|%.2f <= (%.2f/%.2f)=> %.2f|, isChange:%s, isFirst:%s\n",
                //         dB, SILENT_TRESHOLD, silent_change_lowlimit, silent_db, mu_1, silent_change_uplimit,
                //         sound_change_lowlimit, sound_db, mu_2, sound_change_uplimit,
                //         Boolean.toString(isChange), Boolean.toString(isFirst));
                // System.out.printf(
                //         "(Normal State) Detect_No : %d\n", mic_change_count);
            //}
        //} else if (dB > 0) {
            // System.out.printf(
            //         " Monitor : dB : (%.2f/%.2f) \tmu_1: (%.2f/%.2f) \tmu2:(%.2f/%.2f), isChange:%s, isFirst:%s\n",
            //         dB, SILENT_TRESHOLD, silent_db, mu_1, sound_db, mu_2,
            //         Boolean.toString(isChange), Boolean.toString(isFirst));
            //System.out.printf("(In Training)\n");

        }

        // pengecekan terhadap kondisi suara diam,bicara
        // atau terdeteksi terjadi perubahan volume suara
        if (mic_change_state) {

            // perubahan volume suara
            // resetDetection();
            isChange = true;
            mic_change_state = false;

        } else if (dB > SILENT_TRESHOLD || (isChange && dB > MINIMUM_TRESHOLD)) {
            // else if (dB > SILENT_TRESHOLD || (isTraining && dB > 0 && !isFirst)) {

            // suara bicara
            SILENT_PADDING_COUNTER = 0;
            ACTIVE_SILENT_COUNTER = false;
            SILENT_COUNTER = 0;
            //System.out.println("(Bicara...)");
            dBVectorFlag.add(1);
            dBVectorData.add(toObjects(data));
        } else if (dB <= SILENT_TRESHOLD) {

            // suara diam
            SILENT_PADDING_COUNTER++;
            if (SILENT_PADDING_COUNTER <= SILENT_PADDING) {
                //System.out.println("(Diam padding :" + SILENT_PADDING_COUNTER + ")");
                ACTIVE_SILENT_COUNTER = true;
                dBVectorFlag.add(0);
                dBVectorData.add(toObjects(data));
            } else if (ACTIVE_SILENT_COUNTER) {
                // System.out.println(
                //         "(Active Silent :" + dB + ", S_COUNTER :" + SILENT_COUNTER + ")");
                SILENT_COUNTER++;
            }

        }

        // pemotongan suara menjadi utterance, berdasarkan jeda ucapan.
        // atau kondisi diam selama 1.5 detik
        if (dBVectorData.size() * MAX_BUFFER >= CHUNK_TRESHOLD
                || ((SILENT_COUNTER > (((int) (this.audioFormat.getSampleRate() * this.audioFormat.getSampleSizeInBits() / 8) / MAX_BUFFER) * 1.5)
                        && (dBVectorData.size() * MAX_BUFFER > 8400)))) {
            // if voice silent 1.5 second or length audio more than 10 second
            creteUtterance();
        }

    }
    

    public void stopAudio() {

        if (dBVectorData.size() > 25) {
            //create utterance last meeting, if 12800 bytes
            //System.out.println("MASUK");
            creteUtterance();
        }
      
        this.dBVectorFlag.removeAll(dBVectorFlag);
        this.dBVector.removeAll(dBVector);

        try {
            this.baos.close();

        } catch (IOException ex) {
            Logger.getLogger(AudioSegmentor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void training(ArrayList<Double> dB_Vector) {
        double temp_mu1 = 0.0;
        double temp_mu2 = 0.0;
        // double temp_mu1_1 = 0.0;
        // double temp_mu2_2 = 0.0;

        if (isFirst || isChange) {
            //System.out.println("First Training");
            mu_1 = average(dB_Vector) - 0.5;
            mu_2 = average(dB_Vector) + 0.5;
        } else {
            //System.out.println("Second Training");
        }
        temp_mu1 = mu_1;
        // temp_mu1_1 = mu_1;

        temp_mu2 = mu_2;
        // temp_mu2_2 = mu_2;

        ArrayList<Double> category_1 = new ArrayList<>();
        ArrayList<Double> category_2 = new ArrayList<>();

        boolean isStopTraining = false;

        for (int iter = 0; iter < MAX_ITER; iter++) {
            // System.out.println("db_Vector:" + dB_Vector.size());

            for (int i = 0; i < dB_Vector.size(); i++) {
                if (distance(dB_Vector.get(i), mu_1) < distance(dB_Vector.get(i), mu_2)) {
                    category_1.add(dB_Vector.get(i));
                } else {
                    // System.out.println("2");
                    category_2.add(dB_Vector.get(i));
                }
            }

            if (category_1.size() > 0) {
                temp_mu1 = average(category_1);
                // System.out.println("size cat 1:" + category_1.size());
                // System.out.println("average :" + temp_mu1);
            }

            if (category_2.size() > 0) {
                // System.out.println("size cat 2:" + category_2.size());
                // System.out.println("average :" + temp_mu2);
                temp_mu2 = average(category_2);
            }

            if (mu_1 == temp_mu1 && mu_2 == temp_mu2) {
                isStopTraining = true;
            }

            mu_1 = temp_mu1;
            mu_2 = temp_mu2;

            if (mu_1 > mu_2) {
                double replacingTemp = mu_1;
                mu_1 = mu_2;
                mu_2 = replacingTemp;
            }
            ////////////////////////////////////////////////////////////////////////////////

            if (isStopTraining) {
                break;
            }
        }

        ArrayList<Double> u1 = new ArrayList<>();
        ArrayList<Double> u2 = new ArrayList<>();
        for (int i = 0; i < dB_Vector.size(); i++) {
            if (distance(dB_Vector.get(i), mu_1) < distance(dB_Vector.get(i), mu_2)) {
                u1.add(dB_Vector.get(i));
            } else {
                u2.add(dB_Vector.get(i));
            }
        }

        if (u1.size() > 0) {
            for (int i = 0; i < u1.size(); i++) {
                silent_stddev += (mu_1 - u1.get(i)) * (mu_1 - u1.get(i));
                // System.out.println("silent sttdev :" + silent_stddev);
            }
            silent_stddev /= u1.size();
            silent_stddev = Math.sqrt(silent_stddev);
            // System.out.println("silent sttdev :" + silent_stddev);

        }

        if (u2.size() > 0) {
            for (int i = 0; i < u2.size(); i++) {
                sound_stddev += (mu_2 - u2.get(i)) * (mu_2 - u2.get(i));
                // System.out.println(sound_stddev);
            }
            sound_stddev /= u2.size();
            sound_stddev = Math.sqrt(sound_stddev);
        }

        //System.out.printf("std_dev : silent/sound  %f/%f mu_1/mu_2 %f/%f \n", silent_stddev, sound_stddev, mu_1, mu_2);

        SILENT_TRESHOLD = mu_1 + (KONSTANTA_TRESHOLD * distance(mu_1, mu_2));
        if ((isFirst || isChange) && SILENT_TRESHOLD < MINIMUM_TRESHOLD) {
            SILENT_TRESHOLD = MINIMUM_TRESHOLD;
        } else {
            isFirst = false;
            isChange = false;
            // isTraining = false;
        }

        // System.out.println(
        //         "SILENT_TRESHOLD:" + SILENT_TRESHOLD);

        this.handler.onTrehsold(SILENT_TRESHOLD);
        dBVector.clear();
        countTraining = 0;

        // System.exit(0);
    }

    public double distance(double value1, double value2) {

        return Math.sqrt((value1 - value2) * (value1 - value2));

    }

    public double average(ArrayList<Double> category) {
        double count = 0.0;

        for (int i = 0; i < category.size(); i++) {
            count += category.get(i);
        }
        return count / category.size();
    }

    public byte[] toPrimitives(Byte[] oBytes) {
        byte[] bytes = new byte[oBytes.length];

        for (int i = 0; i < oBytes.length; i++) {
            bytes[i] = oBytes[i];
        }

        return bytes;
    }

    public Byte[] toObjects(byte[] bytesPrim) {
        Byte[] bytes = new Byte[bytesPrim.length];

        int i = 0;
        for (byte b : bytesPrim) {
            bytes[i++] = b; // Autoboxing
        }
        return bytes;
    }

    public void resetDetection() {

        monitor_silent_sum = 0;
        monitor_silent_count = 0;
        silent_stddev = 0;
        monitor_sound_sum = 0;
        monitor_sound_count = 0;
        sound_stddev = 0;
        // mic_change_count = 0;

        SILENT_TRESHOLD = MINIMUM_TRESHOLD;

    }

    public void creteUtterance() {
        int n = 0;
        ArrayList<Integer> silentDuration = new ArrayList<>();
        ArrayList<Integer> silentIndex = new ArrayList<>();

        //System.out.println("prepare create utterance...");
        for (int i = 0; i < dBVectorFlag.size(); i++) {
            // System.out.print(dBVectorFlag.get(i));
            if (dBVectorFlag.get(i) == 0) {
                // kondisi diam
                n++;
            } else {
                // transisi diam ke bicara
                silentDuration.add(n);
                silentIndex.add(i);
                n = 0;
            }

            if (i == dBVectorFlag.size() - 1) {
                // posisi terakhir diam
                silentDuration.add(n);
                silentIndex.add(i);
                n = 0;
            }
        }

        for (int i = silentDuration.size() - 1; i >= 0; i--) {

            if (silentDuration.get(i) >= RUNNING_SILENT || isPause) {

                for (int j = 0; j < (silentIndex.get(i) - Math.floor(silentDuration.get(i) / 2)); j++) {
                    dBVectorFlag.remove(0); // 6 //8 //10
                    try {
                        baos.write(toPrimitives(dBVectorData.remove(0)));

                    } catch (IOException ex) {
                        Logger.getLogger(AudioSegmentor.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
               

                if (baos.size() >= 6400) {                   
                    this.handler.onUtterance(baos.toByteArray(),this.channel);
                }

                baos.reset();
                RUNNING_SILENT = SILENT_PADDING;
                countSegmentation = 0;
                if (SILENT_COUNTER > (((int) (this.audioFormat.getSampleRate() * this.audioFormat.getSampleSizeInBits() / 8) ) * 1.5)) {
                    ACTIVE_SILENT_COUNTER = false;
                    SILENT_COUNTER = 0;
                }
                break;
            }
        }

        if (RUNNING_SILENT >= STOP_RUNNING_SILENT) {
            RUNNING_SILENT--;
        } else if (!isChange) {
            countSegmentation++;
            if (countSegmentation == ( ((int) (this.audioFormat.getSampleRate() * this.audioFormat.getSampleSizeInBits() / 8)) * 3)) {

                isChange = true;
                countSegmentation = 0;
            }
        }
    }
}
