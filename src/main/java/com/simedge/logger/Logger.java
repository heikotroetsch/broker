package com.simedge.logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Logger extends Thread {

    public ConcurrentLinkedQueue<String> toWrite = new ConcurrentLinkedQueue<String>();
    private FileWriter fw;
    private PrintWriter pw;

    /**
     * Initializing logger instance thread
     * 
     * @throws IOException
     */
    public Logger() throws IOException {
        var file = Files.createFile(Paths.get("logs/log_" + getCurrentTimeStamp() + ".csv"));
        fw = new FileWriter(file.toFile());
        pw = new PrintWriter(fw, true);
    }

    public void run() {
        while (true) {
            if (!toWrite.isEmpty()) {
                pw.println(toWrite.poll());
                pw.flush();
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Shutdown thread when system closing
     * 
     * @throws IOException
     */
    public void shutdown() throws IOException {
        pw.flush();
        pw.close();
        fw.close();
    }

    /**
     * Get timestamp for logging
     * 
     * @return Returns string of current time as "yyyy-MM-dd'T'HH_mm_ss"
     */
    private static String getCurrentTimeStamp() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss");
        Date now = new Date();
        String result = formatter.format(now);
        return result;
    }

}
