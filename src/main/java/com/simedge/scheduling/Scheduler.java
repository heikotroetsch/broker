package com.simedge.scheduling;

import com.simedge.broker.Sever.Server;
import com.simedge.protocols.MessageTypes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.ejml.simple.SimpleMatrix;

public class Scheduler {
    private static final int landmarkPingNodes = 39;
    private static LinkedList<String> clientOrderDistanceMatrix = new LinkedList<String>();
    private static double[][] distanceMatrix = importLatencyMatrix();
    private static final Object lock = new Object();
    private static SimpleMatrix factorization;
    private static ConcurrentLinkedDeque<Entry<String, Integer>> resourceQue = new ConcurrentLinkedDeque<Entry<String, Integer>>();
    private static ConcurrentHashMap<String, ArrayList<String>> resourceAssignment = new ConcurrentHashMap<String, ArrayList<String>>();

    /**
     * Schedule a resource when requested
     * 
     * @param sourceID                 Source of scheduling request
     * @param requestedNumberResources Number of resources requested
     */
    public static void scheduleResource(String sourceID, int requestedNumberResources) {
        synchronized (lock) {

            Server.logger.toWrite.add("Client List");

            StringBuffer clientList = new StringBuffer();
            for (var c : clientOrderDistanceMatrix) {
                clientList.append(c + ",");
            }
            Server.logger.toWrite.add("Factorization matrix");
            Server.logger.toWrite.add(factorization.toString());
            Server.logger.toWrite.add("sitance matrix");
            Server.logger.toWrite.add(new SimpleMatrix(distanceMatrix).toString());

            int indexOfSource = clientOrderDistanceMatrix.indexOf(sourceID);
            if (indexOfSource == -1) {
                System.out.println(
                        "Source has not been fully added to clientOrderDistanceMatrix so returning without scheduling resource.");
                return;
            }
            var iterator = factorization.iterator(true,
                    indexOfSource, landmarkPingNodes,
                    indexOfSource, factorization.numCols() - 1);
            var resources = new ArrayList<Entry<Integer, Double>>();
            int index = landmarkPingNodes;
            while (iterator.hasNext()) {
                if (index != indexOfSource) {
                    resources.add(new SimpleEntry<Integer, Double>(index, iterator.next()));
                } else {
                    iterator.next();
                }
                index++;
            }

            // if value has been measured put that in the resource list
            for (Entry<Integer, Double> entry : resources) {
                double value;
                if ((value = distanceMatrix[entry.getKey()][clientOrderDistanceMatrix.indexOf(sourceID)]) != 0) {
                    entry.setValue(value);
                }
            }

            Collections.sort(resources, new Comparator<Entry<Integer, Double>>() {

                @Override
                public int compare(Entry<Integer, Double> o1, Entry<Integer, Double> o2) {
                    return o1.getValue().compareTo(o2.getValue());
                }
            });

            System.out.println("Availible Resources: " + resources.toString());

            for (Entry<Integer, Double> entry : resources) {

                if (Server.connections.get(clientOrderDistanceMatrix.get(entry.getKey())).hasResources()
                        && requestedNumberResources > 0) {
                    if (resourceAssignment.get(sourceID) == null) {
                        // if nothing has been assigned yet, initialize arraylist in resource
                        // assignement
                        resourceAssignment.put(sourceID, new ArrayList<String>());
                    }

                    if (!resourceAssignment.get(sourceID)
                            .contains(clientOrderDistanceMatrix.get(entry.getKey()))) {
                        // if resource has not been assigned to this peer than assign it

                        requestedNumberResources--;
                        Server.connections.get(sourceID).messageQueue
                                .add(MessageTypes.GET_RESOURCE + clientOrderDistanceMatrix.get(entry.getKey()) + ";"
                                        + entry.getValue() + System.getProperty("line.separator"));

                        Server.logger.toWrite
                                .add("ScheduledResource" + ";" + sourceID + ";"
                                        + clientOrderDistanceMatrix.get(entry.getKey()) + ";"
                                        + entry.getValue());

                        System.out.println("Message Queue" + Server.connections.get(sourceID).messageQueue);

                        Server.connections.get(clientOrderDistanceMatrix.get(entry.getKey())).decrementResources();
                        resourceAssignment.get(sourceID).add(clientOrderDistanceMatrix.get(entry.getKey()));
                    }

                }

            }

            if (requestedNumberResources > 0) {
                resourceQue.add(new SimpleEntry<String, Integer>(sourceID, requestedNumberResources));
            }

        }

    }

    /**
     * Handle consumer returning resource and updating RTT times
     * 
     * @param source       Source address of consumer
     * @param resourceHash Adddress of provider
     * @param rtt          RTT between consumer and provider
     */
    public static void returnResource(String source, String resourceHash, double rtt) {
        Server.logger.toWrite.add("ConsumerToResourceRTTavg" + ";" + source + ";" + resourceHash + ";" + rtt);

        // update rtt from source to resource in distance matrix
        distanceMatrix[clientOrderDistanceMatrix.indexOf(source)][clientOrderDistanceMatrix
                .indexOf(resourceHash)] = rtt;
        distanceMatrix[clientOrderDistanceMatrix.indexOf(resourceHash)][clientOrderDistanceMatrix
                .indexOf(source)] = rtt;
        // schedule returned resource
        var resource = resourceQue.remove();
        scheduleResource(resource.getKey(), resource.getValue());
        // remove previously assigned at last to prevent rescheduling same resource
        resourceAssignment.get(source).remove(resourceHash);
    }

    /**
     * Remove client from scheudler if leaving the system and initiates latency
     * predictions
     * 
     * @param hash Address that is leaving
     * @return returns true when finished
     */
    public static boolean removeClient(String hash) {
        synchronized (lock) {
            int ignoreIndex = clientOrderDistanceMatrix.indexOf(hash);
            if (ignoreIndex != -1) {
                double temp[][] = new double[distanceMatrix.length - 1][distanceMatrix[0].length - 1];

                int p = 0;
                for (int i = 0; i < distanceMatrix.length; ++i) {
                    if (i == ignoreIndex)
                        continue;

                    int q = 0;
                    for (int j = 0; j < distanceMatrix[0].length; ++j) {
                        if (j == ignoreIndex)
                            continue;

                        temp[p][q] = distanceMatrix[i][j];
                        ++q;
                    }

                    ++p;
                }

                distanceMatrix = temp;
                clientOrderDistanceMatrix.remove(ignoreIndex);
                resourceAssignment.remove(hash);
                for (var entry : resourceAssignment.entrySet()) {
                    if (entry.getValue().remove(hash)) {
                        MessageTypes.RETURN_RESOURCE(Server.connections.get(entry.getKey()), hash);
                    }
                }
                for (var entry : resourceQue) {
                    if (entry.getKey().equals(hash)) {
                        resourceQue.remove(entry);
                    }
                }
                factorization = NMF();
                return true;
            } else {
                return false;
            }
        }

    }

    /**
     * Add resource to scheduler and initiate latency prediction
     * 
     * @param hash         Address string of resource
     * @param measurements Ping measurements to azure zones or other reference nodes
     */
    public static void addClient(String hash, int[] measurements) {
        synchronized (lock) {
            clientOrderDistanceMatrix.add(hash);

            Server.logger.toWrite.add("addClientWithMeasurements" + ";" + hash + ";" + Arrays.toString(measurements));

            double[][] temp = new double[distanceMatrix.length + 1][distanceMatrix[0].length + 1];
            for (int row = 0; row < distanceMatrix.length; row++) {
                for (int col = 0; col < distanceMatrix[row].length; col++) {
                    temp[row][col] = distanceMatrix[row][col];
                }
            }

            for (int i = 0; i < measurements.length; i++) {
                temp[temp.length - 1][i] = measurements[i];
                temp[i][temp[0].length - 1] = measurements[i];
            }
            distanceMatrix = temp;
            factorization = NMF();

            if (!resourceQue.isEmpty()) {
                var resourceRequired = resourceQue.remove();
                scheduleResource(resourceRequired.getKey(), resourceRequired.getValue());
            }

        }

    }

    /**
     * Import distance matrix of reference nodes in this case azures zones from file
     * 
     * @return Distance matrix of reference nodes
     */
    public static double[][] importLatencyMatrix() {
        List<List<String>> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("azure.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                records.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        double[][] matrix = new double[records.size()][records.get(0).size()];

        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                matrix[row][col] = Double.parseDouble(records.get(row).get(col));
            }
        }

        try (BufferedReader br = new BufferedReader(new FileReader("azureZones"))) {
            String line;
            while ((line = br.readLine()) != null) {
                clientOrderDistanceMatrix.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return matrix;
    }

    /**
     * Print matrix for debugging and logging
     * 
     * @param matrix Matrix to log
     */
    public static void printMatrix(double[][] matrix) {
        for (int row = 0; row < matrix.length; row++) {
            System.out.print(clientOrderDistanceMatrix.get(row) + "\t");
            for (int col = 0; col < matrix[row].length; col++) {
                System.out.print(matrix[row][col] + " ");
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * Latency prediction algorithm using non-negative matrix factorization
     * 
     * @return returns factorization results
     */
    private static SimpleMatrix NMF() {

        SimpleMatrix X = SimpleMatrix.random64(distanceMatrix.length, 64, 0, 4, new Random(5432l)); // Initialize random
        SimpleMatrix Y = SimpleMatrix.random64(distanceMatrix.length, 64, 0, 4, new Random(5432l)); // Initialize random

        SimpleMatrix D = new SimpleMatrix(distanceMatrix); // Initialize D data matrix

        // element stream wise copy
        double[][] m = Arrays.stream(distanceMatrix).map(double[]::clone).toArray(double[][]::new);

        for (int row = 0; row < m.length; row++) {
            for (int col = 0; col < m[row].length; col++) {
                if (m[row][col] != 0) {
                    m[row][col] = 1;
                }
            }
        }

        SimpleMatrix M = new SimpleMatrix(m); // Initialize missing values M matrix

        for (int i = 0; i < 200; i++) {
            SimpleMatrix xNumerator = D.elementMult(M).mult(Y);
            SimpleMatrix xDenominator = X.mult(Y.transpose()).elementMult(M).mult(Y);
            X = X.elementMult(xNumerator.elementDiv(xDenominator));

            SimpleMatrix yNumerator = X.transpose().mult(D.elementMult(M));
            SimpleMatrix yDenominator = X.transpose().mult(X.mult(Y.transpose()).elementMult(M));
            Y = Y.elementMult(yNumerator.elementDiv(yDenominator).transpose());
        }

        return (X.mult(Y.transpose()));
    }

}
