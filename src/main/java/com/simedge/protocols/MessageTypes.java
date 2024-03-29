package com.simedge.protocols;

import java.nio.ByteBuffer;
import com.simedge.broker.Sever.Server;
import com.simedge.broker.Sever.ServerThread;
import com.simedge.scheduling.Scheduler;

public class MessageTypes {

    // server mesasges
    public static final int HELLO = 1;
    public static final int BYE = 2;
    public static final int GET_RESOURCE = 3;
    public static final int RETURN_RESOURCE = 4;
    public static final int SET_PING = 5;
    public static final int CHECK_MODEL = 6;
    public static final int MODEL_CACHED = 7;
    public static final int MODEL_EXPIRED = 8;
    public static final int LOAD_MODEL = 9;

    /**
     * Process hello message from consumer
     * 
     * @param source  Thread of source
     * @param content Message content
     */
    public static void process_HELLO(ServerThread source, String content) {

        try {
            source.setIDString(content.split(";")[0]);
            source.setResources(Integer.parseInt(content.split(";")[1]));
            Server.connections.put(source.getIDString(), source);
            System.out.println(Server.connections.toString());

            // get and add ping array
            String[] array = content.split(";");
            int[] pings = new int[array.length - 2];
            for (int i = 2; i < array.length; i++) {
                pings[i - 2] = Integer.parseInt(array[i]);
            }
            source.messageQueue.add("1Added " + source.getIDString() + System.getProperty("line.separator"));

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Scheduler.addClient(source.getIDString(), pings);
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            source.messageQueue.add("0Failed to read message. Either message content empty or resources missing."
                    + System.getProperty("line.separator"));
            System.out.print("Failed to read Hello message. Either message content empty or resources missing.");
        }

    }

    /**
     * Process resource request from consumer
     * 
     * @param source  Server thread of message source
     * @param content Message content
     */
    public static void process_GET_RESOURCE(ServerThread source, String content) {
        content = content.split(";")[0];
        Scheduler.scheduleResource(source.getIDString(), Integer.parseInt(content));

        /*
         * if (resourceID == null || Server.connections.get(resourceID) == null) {
         * source.messageQueue.add("0no resources availible" +
         * System.getProperty("line.separator"));
         * } else {
         * Server.connections.get(resourceID).decrementResources();
         * String resourceReturnMessage = GET_RESOURCE + resourceID;
         * 
         * source.messageQueue.add(resourceReturnMessage +
         * System.getProperty("line.separator"));
         * }
         */

    }

    /**
     * Process consumer returning resource
     * 
     * @param source  Source thread of message
     * @param content Message content
     */
    public static void process_RETURN_RESOURCE(ServerThread source, String content) {
        System.out.println("Full RETURN_RESOURCE Message: " + content);
        var splitMesage = content.split(";");
        String resourceString = splitMesage[0];
        String rttString = splitMesage[1];
        System.out.println("Resource Returned " + resourceString);
        double rtt = Double.parseDouble(rttString);
        ServerThread resource = Server.connections.get(resourceString);

        if (resource == null) {
            System.out.println("0No resource with id " + resourceString + " found");
            source.messageQueue
                    .add("0No resource with id " + resourceString + " found" + System.getProperty("line.separator"));
        } else {
            resource.incrementResources();
            Scheduler.returnResource(source.getIDString(), resourceString, rtt);

        }

    }

    /**
     * Process check model message form consumer
     * 
     * @param source  Source thread of message
     * @param content Message content
     */
    public static void process_CHECK_MODEL(ServerThread source, String content) {
        Server.fillModelCache();
        String hash = content.split(";")[0];

        if (Server.modelCache.containsKey(ByteBuffer.wrap(Server.hexToBytes(hash)))) {
            System.out.println("Model Found " + hash);
            Server.modelCache.get(ByteBuffer.wrap(Server.hexToBytes(hash))).add(source.getIDString());
            source.messageQueue.add(CHECK_MODEL + hash + ";" + 1 + ";" + System.getProperty("line.separator"));
        } else {
            System.out.println("Model Not Found " + hash + " not equal");
            source.messageQueue.add(CHECK_MODEL + hash + ";" + 0 + ";" + System.getProperty("line.separator"));
        }

    }

    /**
     * Process finished upload of model from consumer
     * 
     * @param source  Source thread of message
     * @param content Message content
     */
    public static void process_MODEL_CACHED(ServerThread source, String content) {
        Server.fillModelCache();
        String hash = content.split(";")[0];

        Server.modelCache.get(ByteBuffer.wrap(Server.hexToBytes(hash))).add(source.getIDString());

    }

    /**
     * Process model expired message when consumer evicts model from LRU cache
     * 
     * @param source  Source thread of message
     * @param content Message content
     */
    public static void process_MODEL_EXPIRED(ServerThread source, String content) {
        Server.fillModelCache();
        String hash = content.split(";")[0];

        Server.modelCache.get(ByteBuffer.wrap(Server.hexToBytes(hash))).remove(source.getIDString());

    }

    /**
     * Unimplemented
     * 
     * @param source
     * @param content
     */
    public static void process_SET_PING(ServerThread source, String content) {
        // TODO
    }

    /**
     * Tell consumer to load model from repository
     * 
     * @param source    Source thread of message
     * @param modelHahs Hash of model consumer should download
     */
    public static void LOAD_MODEL(ServerThread source, byte[] modelHash) {
        source.messageQueue
                .add(MODEL_EXPIRED + Server.bytesToHex(modelHash) + ";" + System.getProperty("line.separator"));
    }

    /**
     * Assign resource to consumer
     * 
     * @param source Thread of consumer
     * @param hash   Address of assigned resource
     */
    public static void RETURN_RESOURCE(ServerThread source, String hash) {
        // tells client resource is no longer online
        source.messageQueue.add(RETURN_RESOURCE + hash + System.getProperty("line.separator"));
    }

}
