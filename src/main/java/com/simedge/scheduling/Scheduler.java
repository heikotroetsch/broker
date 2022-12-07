package com.simedge.scheduling;

import com.simedge.broker.Sever.Server;

public class Scheduler {

    public static String scheduleResource(String sourceID) {

        for (String key : Server.connections.keySet()) {
            if (!key.matches(sourceID) && Server.connections.get(key).hasResources()) {
                return key;
            }
        }

        return null;
    }
}
