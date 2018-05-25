package com.mcloyal.demo;

import com.mcloyal.serialport.constant.Cmd;
import com.mcloyal.serialport.utils.ByteUtils;
import com.mcloyal.serialport.utils.Crc16;
import com.mcloyal.serialport.utils.NumberUtil;
import com.mcloyal.serialport.utils.PacketUtils;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void cmd() throws Exception {
//        byte[] dd = Cmd.get207Rept(new byte[]{0x00, 0x00, 0x00, 0x00});
//        System.out.print(ByteUtils.byte2hex(dd));
    }

    @Test
    public void maxInt() throws Exception {
//        byte[] dd=  Cmd.get207Rept(new byte[]{0x00,0x00,0x00,0x00});
        System.out.println(Integer.MAX_VALUE);
        System.out.println(ByteUtils.byte2hex(NumberUtil.intToByte4(Integer.MAX_VALUE)));
    }

    @Test
    public void run() throws Exception {
//        byte[] dd=  Cmd.get207Rept(new byte[]{0x00,0x00,0x00,0x00});
        byte[] frame = new byte[]{0x00, 0x00, 0x00, 0x01};
        byte[] type = new byte[]{0x01, 0x03};
        byte[] packet = PacketUtils.makePackage(frame, type, null);
        System.out.println(ByteUtils.byte2hex(packet));
    }

    @Test
    public void run2() throws Exception {
        byte[] data = new byte[]{0x00, 0x0e, 0x10, 0x00, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x01, 0x03};
        System.out.println("data==" + ByteUtils.byte2hex(data));
        byte[] crc = Crc16.crcCalculateByte(data);
        System.out.println("crc16=" + ByteUtils.byte2hex(crc));
    }

    @Test
    public void cqs() throws Exception {
        String cqss = "AT+CSQ;+cops?;+creg? +CSQ: 23,\n" +
                "0\n" +
                "\n" +
                "+COPS: 0,0,\"CHINA MOBILE\"\n" +
                "\n" +
                "+CREG: 0,1\n" +
                "\n" +
                "OK";
        String sss = "AT+CSQ;+COPS?;+CREG?\n" +
                "\n" +
                "+CSQ: 18,\n" +
                "\n" +
                "+COPS: 0,0,\"CHINA MOBILE\"\n" +
                "\n" +
                "+CREG: 0,1\n" +
                "\n" +
                "OK";

//AT+CSQ;+cops?;+creg?
        String cmd = Cmd.AT._CIP_CSQ_.toUpperCase().trim();
        System.out.println("Cmd.AT._CIP_CSQ_.toUpperCase()==" + cmd);
        if (sss.contains(cmd)) {
            System.out.println("包含");
        }
//        System.out.println("AT+CSQ;+cops?;+creg? +CSQ:".length());
//        String str1 = cqss.substring(27, 29);
//        System.out.println("str1==" + str1);
//        String str2 = sss.substring(28, 30);
//        System.out.println("str2==" + str2);
//
//        int index=sss.indexOf("+CSQ:");
//        System.out.println("index==" + index);
//        String ary[]=sss.split(",");
//        System.out.println("ary[0]==" + ary[0]);
//        System.out.println("信号强度==" + ary[0].substring(ary[0].length()-2,ary[0].length()));
//        for(String ss:ary){
//            System.out.println("ss==" + ss);
//        }
    }

    @Test
    public void zz() throws Exception {
        String ss = "+CSQ: 2,0\n" +
                "\n" +
                "OK";
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(ss);
        if (m.find()) {
            System.out.print(m.group(1));
        }
    }
}