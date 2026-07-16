package com.xss.imageservice.security;

public class MagicNumber {
    public static boolean isImage(byte[] data) {
        if (data.length < 4) return false;
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) return true;
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return true;
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38) return true;
        if (data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                && data.length >= 12
                && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50)
            return true;
        if (data[0] == 0x42 && data[1] == 0x4D) return true;
        return false;
    }
}
