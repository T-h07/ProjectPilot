package com.projectpilot.data.db.auth;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {

    private static final SecureRandom RNG = new SecureRandom();

    // format: PP1$iter$saltB64$hashB64
    private static final String PREFIX = "PP1";

    private final int iterations;

    public PasswordHasher(int workFactor) {
        // workFactor like 10..14; map to safe iteration count
        int wf = Math.max(8, Math.min(20, workFactor));
        this.iterations = 80_000 + (wf * 10_000);
    }

    public String hash(String password) {
        if (password == null) password = "";

        byte[] salt = new byte[16];
        RNG.nextBytes(salt);

        String saltB64 = Base64.getEncoder().encodeToString(salt);
        String hashB64 = pbkdf2(password, salt, iterations);

        return PREFIX + "$" + iterations + "$" + saltB64 + "$" + hashB64;
    }

    public boolean verify(String password, String stored) {
        if (password == null) password = "";
        if (stored == null || stored.isBlank()) return false;

        String[] parts = stored.split("\\$");
        if (parts.length != 4) return false;
        if (!PREFIX.equals(parts[0])) return false;

        int iter;
        try { iter = Integer.parseInt(parts[1]); }
        catch (Exception e) { return false; }

        byte[] salt;
        try { salt = Base64.getDecoder().decode(parts[2]); }
        catch (Exception e) { return false; }

        String expected = parts[3];
        String got = pbkdf2(password, salt, iter);

        return constantTimeEquals(got, expected);
    }

    private static String pbkdf2(String password, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iter, 256);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] out = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new AuthException("Password hashing failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= (a.charAt(i) ^ b.charAt(i));
        return r == 0;
    }
}
