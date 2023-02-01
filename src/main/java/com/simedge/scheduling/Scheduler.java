package com.simedge.scheduling;

import com.simedge.broker.Sever.Server;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.ejml.simple.SimpleMatrix;

public class Scheduler {

    private static LinkedList<String> clientOrderDistanceMatrix = new LinkedList<String>();
    private static double[][] distanceMatrix = importLatencyMatrix();
    private static final Object lock = new Object();
    private static SimpleMatrix factorization;

    public static String scheduleResource(String sourceID) {

        for (String key : Server.connections.keySet()) {
            if (sourceID != key && Server.connections.get(key).hasResources()) {
                return key;
            }
        }

        return sourceID;
    }

    public static String scheduleResource() {
        var avail = Server.connections.keySet();
        int random = (int) (Math.random() * avail.size());
        int i = 0;
        for (String key : Server.connections.keySet()) {
            if (random == i) {
                return key;
            }
            i++;
        }
        return Server.connections.keys().nextElement();
    }

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
                factorization = NMF();
                return true;
            } else {
                return false;
            }
        }

    }

    public static void addClient(String hash, int[] measurements) {
        synchronized (lock) {
            clientOrderDistanceMatrix.add(hash);
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
        }
    }

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

    public static void printMatrix(int[][] matrix) {
        for (int row = 0; row < matrix.length; row++) {
            System.out.print(clientOrderDistanceMatrix.get(row) + "\t");
            for (int col = 0; col < matrix[row].length; col++) {
                System.out.print(matrix[row][col] + " ");
            }
            System.out.println();
        }
        System.out.println();
    }

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
