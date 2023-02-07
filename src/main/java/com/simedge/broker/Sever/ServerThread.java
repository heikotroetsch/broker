package com.simedge.broker.Sever;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.simedge.protocols.MessageTypes;
import com.simedge.scheduling.Scheduler;

public class ServerThread extends Thread {
    private Socket socket;
    private String id;
    private int resources = 10;
    private boolean stop = false;
    public ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<String>();

    public ServerThread(
            Socket socket) {
        this.socket = socket;
    }

    // default run for thread
    public void run() {

        // thread for reading from socket
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream input = socket.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input));

                    do {
                        if (stop) {
                            break;
                        }
                        // reads message type and content. Content is nu ll if empty.
                        int messageType = reader.read() - 48; // 48 is the char number for 0
                        // detect disconnect event
                        if (messageType == (-49)) {
                            shutdown();
                        }
                        System.out.println(messageType);

                        String content = reader.readLine();
                        System.out.println("message type: " + messageType + " content: " + content);
                        // handle message
                        handleMessage(messageType, content);

                    } while (!stop);

                    socket.close();
                } catch (IOException e) {
                    System.out.println("Server exception: " + e.getMessage());
                    shutdown();
                } catch (NullPointerException e) {
                    System.out.println("Server exception: " + e.getMessage());
                    shutdown();
                }
            }
        }).start();

        // thread for writing to socket
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream output = socket.getOutputStream();
                    PrintWriter writer = new PrintWriter(output, true);

                    do {
                        if (stop) {
                            break;
                        }
                        // if write queue is filled write message
                        if (!messageQueue.isEmpty()) {
                            System.out.println("message in queue for: " + id);
                            String message = messageQueue.poll();
                            writer.write(message);
                            writer.flush();
                            System.out.println("message sent: " + message);
                        }

                    } while (!stop);

                    socket.close();
                } catch (IOException e) {
                    System.out.println("Server exception: " + e.getMessage());
                    shutdown();
                } catch (NullPointerException e) {
                    System.out.println("Server exception: " + e.getMessage());
                    shutdown();
                }
            }
        }).start();

    }

    void handleMessage(int messageType, String content) {

        switch (messageType) {
            case MessageTypes.HELLO:
                MessageTypes.process_HELLO(this, content);
                break;

            case MessageTypes.GET_RESOURCE:
                MessageTypes.process_GET_RESOURCE(this, content);
                break;

            case MessageTypes.RETURN_RESOURCE:
                MessageTypes.process_RETURN_RESOURCE(this, content);
                break;

            case MessageTypes.SET_PING:
                MessageTypes.process_SET_PING(this, content);
                break;
            case MessageTypes.CHECK_MODEL:
                MessageTypes.process_CHECK_MODEL(this, content);
                break;

            case MessageTypes.MODEL_CACHED:
                MessageTypes.process_MODEL_CACHED(this, content);
                break;

            case MessageTypes.MODEL_EXPIRED:
                MessageTypes.process_MODEL_EXPIRED(this, content);
                break;

            case MessageTypes.BYE:
                System.out.println("its a bye message from: " + this.id);
                shutdown();
                break;
        }

    }

    void shutdown() {
        System.out.println(Server.connections.toString());
        Server.connections.remove(id);
        Scheduler.removeClient(id);
        System.out.println(Server.connections.toString());

        // removes resource from each model in the cache list
        for (var model : Server.modelCache.values()) {
            model.remove(id);
        }

        this.stop = true;
    }

    public String getIDString() {
        return this.id;
    }

    public void setIDString(String id) {
        this.id = id;
    }

    public void setResources(int i) {
        this.resources = i;
    }

    public void decrementResources() {
        this.resources--;
    }

    public void incrementResources() {
        this.resources++;
    }

    public boolean hasResources() {
        return resources > 0;
    }

}