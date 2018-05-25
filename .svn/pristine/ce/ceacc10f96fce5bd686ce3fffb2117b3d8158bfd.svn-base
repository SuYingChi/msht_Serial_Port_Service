package com.mcloyal.serialport.utils;

/**
 * 作者：huangzhong on 2017/2/12 10:16
 * 邮箱：mcloyal@163.com
 */

/**
 * 字节工具类
 */
public class ByteUtils {
    /**
     * 字节数组转换成十六进制输出
     *
     * @param buffer
     * @return
     */
    public static String byte2hex(byte[] buffer) {
        String h = "";
        for (int i = 0; i < buffer.length; i++) {
            String temp = Integer.toHexString(buffer[i] & 0xFF);
            if (temp.length() == 1) {
                temp = "0" + temp;
            }
            h = h + " " + temp;
        }
        return h;
    }

    /**
     * 将单个字节进行16进制输出
     *
     * @param b
     * @return
     */
    public static String toHex(byte b) {
        String result = Integer.toHexString(b & 0xFF);
        if (result.length() == 1) {
            result = '0' + result;
        }
        return result;
    }

    /**
     * 将data字节型数据转换为0~255 (0xFF即BYTE)。
     *
     * @param data
     * @return
     */
    public static byte getUnsignedByte(byte data) {
        return (byte) (data & 0xFF);
    }


    public static byte[] ByteTobyte(Byte[] data) {
        byte[] cc = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            cc[i] = data[i].byteValue();
        }
        return cc;
    }
}
