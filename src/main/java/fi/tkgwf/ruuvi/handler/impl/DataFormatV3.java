package fi.tkgwf.ruuvi.handler.impl;

import fi.tkgwf.ruuvi.Config;
import fi.tkgwf.ruuvi.Main;
import fi.tkgwf.ruuvi.utils.RuuviUtils;
import org.influxdb.dto.Point;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DataFormatV3 {
    // For some reason the latest sensortag data format has four null bytes at the end, changing the length of the raw packer (third byte is the length)
    private static final String OLDER_SENSORTAG_BEGINS = "> 04 3E 21 02 01 03 01 ";
    private static final String SENSORTAG_BEGINS = "> 04 3E 25 02 01 03 01 ";
    private String latestMac = null;

    public DataFormatV3() {
    }

    public void read(String rawLine) {
        if (latestMac == null && (rawLine.startsWith(SENSORTAG_BEGINS) || rawLine.startsWith(OLDER_SENSORTAG_BEGINS))) {
            // line with Ruuvi MAC
            latestMac = RuuviUtils.getMacFromLine(rawLine.substring(SENSORTAG_BEGINS.length()));
        } else if (latestMac != null) {
            try {
                handleMeasurement(latestMac, rawLine);
                //TODO: log errors
            } finally {
                latestMac = null;
            }
        } else {
            //TODO: log error
        }
    }

    public void reset() {
        latestMac = null;
    }

    private void handleMeasurement(String mac, String rawLine) {
        rawLine = rawLine.trim();
        rawLine = rawLine.substring(rawLine.indexOf(' ') + 1, rawLine.lastIndexOf(' ')); // discard first and last byte (TODO: why?)
        byte[] data = RuuviUtils.hexToBytes(rawLine);
        if (data[0] != 3) {
            return; // unknown type
        }
        String protocolVersion = String.valueOf(data[0]);

        float humidity = ((float) (data[1] & 0xFF)) / 2f;

        int temperatureSign = (data[2] >> 7) & 1;
        int temperatureBase = (data[2] & 0x7F);
        float temperatureFraction = ((float) data[3]) / 100f;
        float temperature = ((float) temperatureBase) + temperatureFraction;
        if (temperatureSign == 1) {
            temperature *= -1;
        }

        int pressureHi = data[4] & 0xFF;
        int pressureLo = data[5] & 0xFF;
        int pressure = pressureHi * 256 + 50000 + pressureLo;

        float accelX = (data[6] << 8 | data[7] & 0xFF) / 1000f;
        float accelY = (data[8] << 8 | data[9] & 0xFF) / 1000f;
        float accelZ = (data[10] << 8 | data[11] & 0xFF) / 1000f;
        double accelTotal = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);

        int battHi = data[12] & 0xFF;
        int battLo = data[13] & 0xFF;
        float battery = (battHi * 256 + battLo) / 1000f;

        Point point1 = Point.measurement(mac)
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .addField("temperature", temperature)
            .addField("humidity", humidity)
            .addField("pressure", pressure)
            .addField("acceleration_x", accelX)
            .addField("acceleration_y", accelY)
            .addField("acceleration_z", accelZ)
            .addField("acceleration_total", accelTotal)
            .addField("batteryVoltage", battery)
            .build();

        Main.influx.write(Config.INFLUX_DATABASENAME, "autogen", point1);
    }
}
