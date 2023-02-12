package com.simedge.broker.Sever;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

import com.simedge.logger.Logger;

public class Server {

    private static int PORT = 12345;
    public static ConcurrentHashMap<String, ServerThread> connections = new ConcurrentHashMap<String, ServerThread>();
    public static ConcurrentHashMap<ByteBuffer, LinkedHashSet<String>> modelCache = new ConcurrentHashMap<ByteBuffer, LinkedHashSet<String>>();
    public static Logger logger;

    public static void main(String[] args) {

        // Starting logger
        try {
            logger = new Logger();
            logger.start();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Filling model Cache
        System.out.println("Writing file names to memory");
        fillModelCache();

        // Start server
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Server is listening on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                new ServerThread(socket).start();
            }

        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void fillModelCache() {
        File[] models = new File("modelCache/").listFiles();
        System.out.println(Arrays.toString(models));
        for (File file : models) {
            System.out.println(file.getName());
            modelCache.putIfAbsent(ByteBuffer.wrap(hexToBytes(file.getName())), new LinkedHashSet<String>());
        }
    }

    public static String bytesToHex(byte[] bytes) {
        final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
                '9', 'a', 'b', 'c', 'd', 'e', 'f' };
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }

}
