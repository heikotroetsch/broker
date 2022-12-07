package com.simedge.protocols;

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

    public static void process_HELLO(ServerThread source, String content) {

        try {
            source.setIDString(content.split(";")[0]);
            source.setResources(Integer.parseInt(content.split(";")[1]));
            Server.connections.put(source.getIDString(), source);
            System.out.println(Server.connections.toString());
            source.messageQueue.add("1Added " + source.getIDString() + System.getProperty("line.separator"));
        } catch (Exception e) {
            source.messageQueue.add("0Failed to read message. Either message content empty or resources missing."
                    + System.getProperty("line.separator"));
            System.out.print("Failed to read Hello message. Either message content empty or resources missing.");
        }

    }

    public static void process_GET_RESOURCE(ServerThread source, String content) {
        String resourceID = Scheduler.scheduleResource(source.getIDString());

        if (resourceID == null || Server.connections.get(resourceID) == null) {
            source.messageQueue.add("0no resources availible" + System.getProperty("line.separator"));
        } else {
            Server.connections.get(resourceID).decrementResources();
            String resourceReturnMessage = GET_RESOURCE + resourceID;

            source.messageQueue.add(resourceReturnMessage + System.getProperty("line.separator"));
        }

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

    public static void process_SET_PING(ServerThread source, String content) {
        // TODO
    }

}
