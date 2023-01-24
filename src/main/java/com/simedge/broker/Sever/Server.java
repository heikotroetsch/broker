package com.simedge.broker.Sever;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;

public class Server {

    private static int PORT = 12345;
    public static ConcurrentHashMap<String, ServerThread> connections = new ConcurrentHashMap<String, ServerThread>();
    public static ConcurrentHashMap<ByteBuffer, byte[]> modelCache = new ConcurrentHashMap<ByteBuffer, byte[]>();

    public static void main(String[] args) {
        // Filling model Cache
        try {
            System.out.println("Writing cache files to memory");
            fillModelCache();
        } catch (IOException e) {
            System.err.println("File problems during loading files from disk into cache");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No Such hashing alogrithm");
        }

        // Adding shutdown hook which saves models to files when exiting
        Thread SystemExitHook = new Thread(() -> {
            System.out.println("Shutting down and saving cache to disk");
            try {
                saveModelChacheToDisk();
            } catch (IOException e) {
                System.err.println("File problems during cache save to disk");
            }
        });
        Runtime.getRuntime().addShutdownHook(SystemExitHook);

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

    private static void fillModelCache() throws IOException, NoSuchAlgorithmException {
        File[] models = new File("modelCache/").listFiles();
        System.out.println(Arrays.toString(models));
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        for (File file : models) {
            byte[] fileBytes = FileUtils.readFileToByteArray(file);
            System.out.println(bytesToHex(md.digest(fileBytes)));
            modelCache.put(ByteBuffer.wrap(md.digest(fileBytes)), fileBytes);
        }
    }

    private static void saveModelChacheToDisk() throws IOException {
        for (var file : modelCache.entrySet()) {
            Files.write(Path.of("modelCache/", bytesToHex(file.getKey().array())), file.getValue());
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
