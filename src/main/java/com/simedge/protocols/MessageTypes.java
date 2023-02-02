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
            Scheduler.addClient(source.getIDString(), pings);
            source.messageQueue.add("1Added " + source.getIDString() + System.getProperty("line.separator"));
        } catch (Exception e) {
            source.messageQueue.add("0Failed to read message. Either message content empty or resources missing."
                    + System.getProperty("line.separator"));
            System.out.print("Failed to read Hello message. Either message content empty or resources missing.");
        }

    }

    public static void process_GET_RESOURCE(ServerThread source, String content) {
        Scheduler.scheduleResource(source.getIDString());

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

    public static void process_RETURN_RESOURCE(ServerThread source, String content) {

        ServerThread resource = Server.connections.get(content);

        if (resource == null) {
            System.out.println("0No resource with id " + content + " found");
            source.messageQueue
                    .add("0No resource with id " + content + " found" + System.getProperty("line.separator"));
        } else {
            resource.incrementResources();
        }

    }

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

    public static void process_MODEL_CACHED(ServerThread source, String content) {
        Server.fillModelCache();
        String hash = content.split(";")[0];

        Server.modelCache.get(ByteBuffer.wrap(Server.hexToBytes(hash))).add(source.getIDString());

    }

    public static void process_MODEL_EXPIRED(ServerThread source, String content) {
        Server.fillModelCache();
        String hash = content.split(";")[0];

        Server.modelCache.get(ByteBuffer.wrap(Server.hexToBytes(hash))).remove(source.getIDString());

    }

    public static void process_SET_PING(ServerThread source, String content) {
        // TODO
    }

    public static void LOAD_MODEL(ServerThread source, byte[] modelHash) {
        source.messageQueue
                .add(MODEL_EXPIRED + Server.bytesToHex(modelHash) + ";" + System.getProperty("line.separator"));
    }

}
