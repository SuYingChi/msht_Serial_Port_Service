package com.mcloyal.serialport.utils;

import com.mcloyal.serialport.entity.Packet;
import com.mcloyal.serialport.exception.AnalysisException;
import com.mcloyal.serialport.service.PortService;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 数据包拆分
 * Created by rain on 2017/5/13.
 */
public class AnalysisUtils {

    /**
     * 拆分数据包
     *
     * @param buffer
     * @param length
     * @return
     * @throws AnalysisException
     */
    public static Packet analysisFrame(byte[] buffer, int length) throws AnalysisException {
        if (buffer == null || buffer.length != length) {
            throw new AnalysisException("data为空或者长度不是" + length + "位");
        }
        Packet packet = null;
        try {
            packet = new Packet();
            packet.setStart(buffer[0]);//包头
            byte[] len = Arrays.copyOfRange(buffer, 1, 3);
            packet.setLen(len);//长度,去除包头以外的长度
            byte version = buffer[3];
            packet.setVersion(version);//版本
            byte back1 = buffer[4];
            packet.setBack1(back1);//备用1字节
            byte[] frame = Arrays.copyOfRange(buffer, 5, 9);
            packet.setFrame(frame);//数据帧序
            byte[] back2 = Arrays.copyOfRange(buffer, 9, 11);
            packet.setBack2(back2);//备用2字节
            byte[] cmd = Arrays.copyOfRange(buffer, 11, 13);
            packet.setCmd(cmd);//数据类型
            byte[] data = Arrays.copyOfRange(buffer, 13, buffer.length - 2);
            if (data != null && data.length > 0) {
                ArrayList<Byte> bData = new ArrayList();
                for (byte temp : data) {
                    bData.add(temp);
                }
                packet.setData(bData);//数据内容
            }
            byte crc[] = Arrays.copyOfRange(buffer, buffer.length - 2, buffer.length);
            packet.setCrc(crc);
        } catch (Exception e) {
            throw new AnalysisException("截取数据错误");
        }
        return packet;
    }
}
