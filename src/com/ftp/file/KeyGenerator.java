package com.ftp.file;

/**
 * This class is used for generating key between server and client using Diffie Hellman algorithm.
 * Numbers are generated randomly.
 */
public class KeyGenerator {
    public static final long n = 65535;
    public static final long g = 255;
    private long a;
    private long finalCode;

    public KeyGenerator(long a) {
        this.a = a;
    }

    /**
     * Code that needs to be combined on other side (server or client, depending who's sending) to create key
     *
     * @return Code to send
     */
    public long getCodeToSend() {
        return (long) Math.pow(g, a) % n;
    }

    /**
     * Combining other side (server or client, depending who already sent) code with current code to create key.
     *
     * @param code Code that needs to be combined
     */
    public void setReceivedCode(long code) {
        finalCode = (long) Math.pow(code, a);
    }

    /**
     * Returns created key
     *
     * @return Key
     */
    public long getFinalCode() {
        return finalCode;
    }
}
