package de.kai_morich.simple_bluetooth_le_terminal;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class EscortAdvCrypto {

    private byte[] ctcData = new byte[16];
    private byte[] ctcKey = new byte[16];;
    private byte[] aesKey = new byte[16];

    EscortAdvCrypto(byte[] ctcData, byte[] ctcKey, String deviceName, String btMac, long password) {
        if (deviceName == null) return;

        System.arraycopy(ctcData, 0, this.ctcData, 0, this.ctcData.length);
        System.arraycopy(ctcKey, 0, this.ctcKey, 0, this.ctcKey.length);

        String[] macAddressParts = btMac.split(":");
        byte[] macAddressBytes = new byte[6];
        for (int i = 0; i < 6; i++){
            int hex = Integer.parseInt(macAddressParts[i], 16);
            macAddressBytes[5 - i] = (byte) hex;
        }

        System.arraycopy(macAddressBytes, 0, aesKey, 0, 6);

        aesKey[6] = (byte) deviceName.charAt(0);
        aesKey[7] = (byte) deviceName.charAt(1);
        for (int i = 0; i < 4; i++) aesKey[i + 8] = (byte) deviceName.charAt(i + 5);
        for (int i = 0; i < 4; i++) aesKey[i + 12] = (byte) (password >> 8 * i);
    }

    public byte[] decryptPacket(byte[] packet) {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        byte[] aesData = getCtcPrng(packet[packet.length - 1]);

        Key secretKey = new SecretKeySpec(aesKey, "AES");
        try {
            assert cipher != null;
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        byte[] encryptedAesData = null;
        try {
            encryptedAesData = cipher.doFinal(aesData);
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        byte[] result = new byte[packet.length - 1];

        assert encryptedAesData != null;
        for (int i = 0; i < result.length; i++) result[i] = (byte) (packet[i] ^ encryptedAesData[i]);


        return result;
    }

    private byte[] getCtcPrng(byte nonce) {
        ctcData[0] = nonce;
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }

        Key secretKey = new SecretKeySpec(ctcKey, "AES");
        try {
            assert cipher != null;
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] result = null;
        try {
            result = cipher.doFinal(ctcData);
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        return result;
    }
}
