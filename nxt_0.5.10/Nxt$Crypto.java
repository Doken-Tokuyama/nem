import java.io.*;
import java.security.*;
import java.util.*;

static class Crypto
{
    static byte[] getPublicKey(final String secretPhrase) {
        try {
            final byte[] publicKey = new byte[32];
            Curve25519.keygen(publicKey, null, Nxt.getMessageDigest("SHA-256").digest(secretPhrase.getBytes("UTF-8")));
            return publicKey;
        }
        catch (RuntimeException | UnsupportedEncodingException e) {
            Nxt.logMessage("Error getting public key", e);
            return null;
        }
    }
    
    static byte[] sign(final byte[] message, final String secretPhrase) {
        try {
            final byte[] P = new byte[32];
            final byte[] s = new byte[32];
            final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
            Curve25519.keygen(P, s, digest.digest(secretPhrase.getBytes("UTF-8")));
            final byte[] m = digest.digest(message);
            digest.update(m);
            final byte[] x = digest.digest(s);
            final byte[] Y = new byte[32];
            Curve25519.keygen(Y, null, x);
            digest.update(m);
            final byte[] h = digest.digest(Y);
            final byte[] v = new byte[32];
            Curve25519.sign(v, h, x, s);
            final byte[] signature = new byte[64];
            System.arraycopy(v, 0, signature, 0, 32);
            System.arraycopy(h, 0, signature, 32, 32);
            return signature;
        }
        catch (RuntimeException | UnsupportedEncodingException e) {
            Nxt.logMessage("Error in signing message", e);
            return null;
        }
    }
    
    static boolean verify(final byte[] signature, final byte[] message, final byte[] publicKey) {
        try {
            final byte[] Y = new byte[32];
            final byte[] v = new byte[32];
            System.arraycopy(signature, 0, v, 0, 32);
            final byte[] h = new byte[32];
            System.arraycopy(signature, 32, h, 0, 32);
            Curve25519.verify(Y, v, h, publicKey);
            final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
            final byte[] m = digest.digest(message);
            digest.update(m);
            final byte[] h2 = digest.digest(Y);
            return Arrays.equals(h, h2);
        }
        catch (RuntimeException e) {
            Nxt.logMessage("Error in Nxt.Crypto verify", e);
            return false;
        }
    }
}
