import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.json.JSONObject;

public class SSS{
    
    static class Point {
        final BigInteger x;
        final BigInteger y;
        final int base;
        final String rawValue;

        Point(BigInteger x, BigInteger y, int base, String rawValue) {
            this.x = x;
            this.y = y;
            this.base = base;
            this.rawValue = rawValue;
        }

        @Override
        public String toString() {
            return String.format("x=%s: base=%d value='%s' -> decimal=%s", x, base, rawValue, y);
        }
    }

    public static void solveAndValidate(String jsonInput) {
        
        JSONObject json = new JSONObject(jsonInput);
        JSONObject keysInfo = json.getJSONObject("keys");
        int k = keysInfo.getInt("k");

        List<Point> allPoints = new ArrayList<>();
        List<String> sortedKeys = new ArrayList<>(json.keySet());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            if (key.equals("keys")) continue;

            BigInteger x = new BigInteger(key);
            JSONObject share = json.getJSONObject(key);
            String valueStr = share.getString("value");
            int base = Integer.parseInt(share.getString("base"));
            BigInteger y = new BigInteger(valueStr, base);
            allPoints.add(new Point(x, y, base, valueStr));
        }

        
        List<List<Point>> combinations = new ArrayList<>();
        findCombinations(allPoints, k, 0, new ArrayList<>(), combinations);

        Map<BigInteger, Integer> secretFrequencies = new HashMap<>();
        Map<BigInteger, List<Point>> secretToCombo = new HashMap<>();

        for (List<Point> combo : combinations) {
            BigInteger secret = lagrangeInterpolationAtZero(combo);
            secretFrequencies.put(secret, secretFrequencies.getOrDefault(secret, 0) + 1);
            if (!secretToCombo.containsKey(secret)) {
                secretToCombo.put(secret, combo);
            }
        }

        BigInteger majoritySecret = null;
        int maxFrequency = 0;
        for (Map.Entry<BigInteger, Integer> entry : secretFrequencies.entrySet()) {
            if (entry.getValue() > maxFrequency) {
                maxFrequency = entry.getValue();
                majoritySecret = entry.getKey();
            }
        }

        if (majoritySecret == null) {
            System.out.println("Could not determine a majority secret.");
            return;
        }

        

        
        System.out.println("Secret Key: " + majoritySecret);

        
        List<Point> winningCombination = secretToCombo.get(majoritySecret);
        BigInteger[] coefficients = reconstructPolynomial(winningCombination);
        List<Point> invalidShares = new ArrayList<>();

        for (Point p : allPoints) {
            BigInteger expectedY = evaluatePolynomial(coefficients, p.x);
            if (!p.y.equals(expectedY)) {
                invalidShares.add(p);
            }
        }

        
        if (!invalidShares.isEmpty()) {
            System.out.println("\nInvalid Shares Found:");
            for (Point p : invalidShares) {
                BigInteger expectedY = evaluatePolynomial(coefficients, p.x);
                System.out.printf("  x=%s (base=%d, raw='%s', decimal=%s) -> expected %s\n", p.x, p.base, p.rawValue, p.y, expectedY);
            }
        }
        
    }

    
    private static void findCombinations(List<Point> points, int k, int start, List<Point> currentCombo, List<List<Point>> allCombos) {
        if (currentCombo.size() == k) {
            allCombos.add(new ArrayList<>(currentCombo));
            return;
        }
        for (int i = start; i < points.size(); i++) {
            currentCombo.add(points.get(i));
            findCombinations(points, k, i + 1, currentCombo, allCombos);
            currentCombo.remove(currentCombo.size() - 1);
        }
    }

    
    private static BigInteger evaluatePolynomial(BigInteger[] coeffs, BigInteger x) {
        BigInteger result = BigInteger.ZERO;
        for (int i = coeffs.length - 1; i >= 0; i--) {
            result = result.multiply(x).add(coeffs[i]);
        }
        return result;
    }

    
    private static BigInteger lagrangeInterpolationAtZero(List<Point> points) {
        BigInteger secret = BigInteger.ZERO;
        for (int j = 0; j < points.size(); j++) {
            Point currentPoint = points.get(j);
            BigInteger y_j = currentPoint.y;
            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for (int i = 0; i < points.size(); i++) {
                if (i == j) continue;
                Point otherPoint = points.get(i);
                numerator = numerator.multiply(otherPoint.x);
                denominator = denominator.multiply(otherPoint.x.subtract(currentPoint.x));
            }
            BigInteger term = y_j.multiply(numerator).divide(denominator);
            secret = secret.add(term);
        }
        return secret;
    }

    
    private static BigInteger[] reconstructPolynomial(List<Point> points) {
        int k = points.size();
        BigInteger[] totalPolynomial = new BigInteger[k];
        Arrays.fill(totalPolynomial, BigInteger.ZERO);

        for (int j = 0; j < k; j++) {
            Point currentPoint = points.get(j);
            BigInteger[] numeratorPoly = new BigInteger[k];
            Arrays.fill(numeratorPoly, BigInteger.ZERO);
            numeratorPoly[0] = BigInteger.ONE;
            
            BigInteger denominator = BigInteger.ONE;

            for (int i = 0; i < k; i++) {
                if (i == j) continue;
                Point otherPoint = points.get(i);
                
                for (int p = k - 1; p > 0; p--) {
                    numeratorPoly[p] = numeratorPoly[p-1].subtract(numeratorPoly[p].multiply(otherPoint.x));
                }
                numeratorPoly[0] = numeratorPoly[0].multiply(otherPoint.x.negate());
                
                denominator = denominator.multiply(currentPoint.x.subtract(otherPoint.x));
            }
            
            BigInteger scale = currentPoint.y.divide(denominator);
            for (int i = 0; i < k; i++) {
                totalPolynomial[i] = totalPolynomial[i].add(numeratorPoly[i].multiply(scale));
            }
        }
        return totalPolynomial;
    }

    public static void main(String[] args) {
        String filePath = ".\\testcase.json";
        try {
            String jsonInput = Files.readString(Paths.get(filePath));
            solveAndValidate(jsonInput);
            
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}