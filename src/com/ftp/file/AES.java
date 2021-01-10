package com.ftp.file;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Class is used for encrypting and decrypting bytes that needs to be sent. It uses AES 128bit encryption algorithm.
 */
public class AES {

    private static SecretKeySpec secretKey;

    /**
     * Setting key for encryption.
     *
     * @param myKey Key as string that is needed for creating {@link SecretKeySpec}
     */
    public static void setKey(String myKey) {
        MessageDigest sha;
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypt byte array using given encryption algorithm
     *
     * @param arr    Array to be encrypted
     * @param secret Key
     * @return Encrypted byte array
     */
    public static byte[] encrypt(byte[] arr, String secret) {
        try {
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(arr);
        } catch (Exception e) {
            System.out.println("Error while encrypting: " + e.toString());
        }
        return null;
    }

    /**
     * Decrypt byte array using given encryption algorithm
     *
     * @param arr    Array to be decrypted
     * @param secret Key
     * @return Decrypted byte array
     */
    public static byte[] decrypt(byte[] arr, String secret) {
        try {
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(arr);
        } catch (Exception e) {
            System.out.println("Error while decrypting: " + e.toString());
        }
        return null;
    }
}