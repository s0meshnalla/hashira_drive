
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class SecretReconstruction {

    private static BigInteger toBigInt(String val, int radix) {
        return new BigInteger(val, radix);
    }

    private static BigInteger interpolateAtZero(List<Integer> xVals, List<BigInteger> yVals, int threshold) {
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;

        for (int i = 0; i < threshold; i++) {
            BigInteger partialNum = BigInteger.ONE;
            BigInteger partialDen = BigInteger.ONE;

            for (int j = 0; j < threshold; j++) {
                if (i == j) {
                    continue;
                }
                partialNum = partialNum.multiply(BigInteger.valueOf(-xVals.get(j)));
                partialDen = partialDen.multiply(BigInteger.valueOf(xVals.get(i) - xVals.get(j)));
            }

            numerator = numerator.multiply(partialDen)
                    .add(yVals.get(i).multiply(partialNum).multiply(denominator));
            denominator = denominator.multiply(partialDen);

            BigInteger gcd = numerator.gcd(denominator);
            if (!gcd.equals(BigInteger.ONE)) {
                numerator = numerator.divide(gcd);
                denominator = denominator.divide(gcd);
            }
        }

        if (denominator.equals(BigInteger.valueOf(-1))) {
            numerator = numerator.negate();
            denominator = BigInteger.ONE;
        }

        if (!denominator.equals(BigInteger.ONE)) {
            throw new ArithmeticException("Interpolation did not yield integer result: "
                    + numerator + "/" + denominator);
        }

        return numerator;
    }

    private static Map<String, Map<String, String>> simpleJsonParser(String raw) {
        Map<String, Map<String, String>> parsed = new LinkedHashMap<>();

        String content = raw.trim();
        content = content.substring(1, content.length() - 1);

        String[] blocks = content.split("},");
        for (String block : blocks) {
            if (!block.endsWith("}")) {
                block += "}";
            }
            int sepIndex = block.indexOf(":");
            if (sepIndex < 0) {
                continue;
            }

            String mainKey = block.substring(0, sepIndex).replaceAll("[\"{}]", "").trim();
            String inner = block.substring(sepIndex + 1).replaceAll("[\"{}]", "").trim();

            Map<String, String> innerMap = new HashMap<>();
            for (String entry : inner.split(",")) {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    innerMap.put(parts[0].trim(), parts[1].trim());
                }
            }
            parsed.put(mainKey, innerMap);
        }

        return parsed;
    }

    public static void main(String[] args) throws IOException {
        String jsonInput = new String(Files.readAllBytes(Paths.get("testcase.json")));

        Map<String, Map<String, String>> data = simpleJsonParser(jsonInput);

        Map<String, String> keyInfo = data.get("keys");
        int totalShares = Integer.parseInt(keyInfo.get("n"));
        int minShares = Integer.parseInt(keyInfo.get("k"));

        List<Integer> xVals = new ArrayList<>();
        List<BigInteger> yVals = new ArrayList<>();

        for (String label : data.keySet()) {
            if ("keys".equals(label)) {
                continue;
            }
            int x = Integer.parseInt(label);
            int base = Integer.parseInt(data.get(label).get("base"));
            BigInteger y = toBigInt(data.get(label).get("value"), base);

            xVals.add(x);
            yVals.add(y);
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < xVals.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(xVals::get));

        List<Integer> chosenX = new ArrayList<>();
        List<BigInteger> chosenY = new ArrayList<>();
        for (int idx = 0; idx < minShares; idx++) {
            chosenX.add(xVals.get(order.get(idx)));
            chosenY.add(yVals.get(order.get(idx)));
        }

        BigInteger secret = interpolateAtZero(chosenX, chosenY, minShares);
        System.out.println("Recovered Secret (decimal): " + secret);
        System.out.println("Recovered Secret (hex): " + secret.toString(16));
    }
}
