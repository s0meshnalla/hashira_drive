import org.json.*;
import java.nio.file.*;
import java.math.*;
import java.util.*;
import java.util.regex.Pattern;

public class ShamirSecretSharingFixed {

    // Exact fraction for rational Lagrange
    static final class Frac {
        final BigInteger num; // normalized
        final BigInteger den; // > 0
        Frac(BigInteger n, BigInteger d) {
            if (d.signum() == 0) throw new ArithmeticException("denominator is zero");
            if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
            BigInteger g = n.gcd(d);
            this.num = n.divide(g);
            this.den = d.divide(g);
        }
        Frac add(Frac o) {
            return new Frac(this.num.multiply(o.den).add(o.num.multiply(this.den)),
                            this.den.multiply(o.den));
        }
        Frac mul(Frac o) {
            return new Frac(this.num.multiply(o.num), this.den.multiply(o.den));
        }
    }

    private static final Pattern INT_KEY = Pattern.compile("\\d+");

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java ShamirSecretSharingFixed <path-to-json>");
            System.exit(1);
        }
    String content = Files.readString(Path.of(args[0]));
        Object root = new JSONTokener(content).nextValue();

        List<JSONObject> cases = normalizeTestCases(root);
        int idx = 1;
        for (JSONObject tc : cases) {
            BigInteger c = solveCase(tc);
            System.out.println("Test case " + idx + ": C = " + c.toString());
            idx++;
        }
    }

    // Normalize root into a list of case objects
    private static List<JSONObject> normalizeTestCases(Object root) {
        List<JSONObject> out = new ArrayList<>();
        if (root instanceof JSONArray) {
            JSONArray arr = (JSONArray) root;
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } else if (root instanceof JSONObject) {
            JSONObject obj = (JSONObject) root;
            if (obj.has("test_cases") && obj.get("test_cases") instanceof JSONArray) {
                JSONArray arr = obj.getJSONArray("test_cases");
                for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
            } else {
                out.add(obj);
            }
        } else {
            throw new IllegalArgumentException("Unsupported JSON root");
        }
        return out;
    }

    private static BigInteger solveCase(JSONObject tc) {
        int k = 3; // default (quadratic) if missing
        if (tc.has("keys")) {
            JSONObject keys = tc.getJSONObject("keys");
            if (keys.has("k")) k = keys.getInt("k");
        }

        // Collect (x,y) from numeric keys
        List<BigInteger> xs = new ArrayList<>();
        List<BigInteger> ys = new ArrayList<>();
        for (String key : tc.keySet()) {
            if (!INT_KEY.matcher(key).matches()) continue;
            int x = Integer.parseInt(key);
            JSONObject yObj = tc.getJSONObject(key);
            int base = yObj.getInt("base");
            String valStr = String.valueOf(yObj.get("value"));
            BigInteger y = new BigInteger(valStr, base); // decode base/value
            xs.add(BigInteger.valueOf(x));
            ys.add(y);
        }
        if (xs.size() < k) {
            throw new IllegalArgumentException("Not enough shares: have " + xs.size() + ", need " + k);
        }

        // Deterministic subset: smallest k x-values
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < xs.size(); i++) order.add(i);
        order.sort(Comparator.comparing(xs::get));

        List<BigInteger> xsK = new ArrayList<>();
        List<BigInteger> ysK = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            xsK.add(xs.get(order.get(i)));
            ysK.add(ys.get(order.get(i)));
        }

        return lagrangeAtZeroExact(xsK, ysK);
    }

    // Lagrange interpolation at x = 0 with exact arithmetic
    private static BigInteger lagrangeAtZeroExact(List<BigInteger> xs, List<BigInteger> ys) {
        if (xs.size() != ys.size()) throw new IllegalArgumentException("Mismatched sizes");
        int k = xs.size();
        Frac sum = new Frac(BigInteger.ZERO, BigInteger.ONE);

        for (int i = 0; i < k; i++) {
            Frac li0 = new Frac(BigInteger.ONE, BigInteger.ONE);
            for (int j = 0; j < k; j++) {
                if (i == j) continue;
                BigInteger num = xs.get(j).negate();                // (-x_j)
                BigInteger den = xs.get(i).subtract(xs.get(j));     // (x_i - x_j)
                li0 = li0.mul(new Frac(num, den));
            }
            sum = sum.add(new Frac(ys.get(i), BigInteger.ONE).mul(li0));
        }

        if (!sum.den.equals(BigInteger.ONE)) {
            throw new ArithmeticException("Non-integer secret; check inputs or degree/threshold");
        }
        return sum.num;
    }
}
