package fi.tkgwf.ruuvi.handler.impl;

import fi.tkgwf.ruuvi.Config;
import fi.tkgwf.ruuvi.Main;
import fi.tkgwf.ruuvi.utils.RuuviUtils;
import org.influxdb.dto.Point;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class DataFormatV2V4 {
    private static final String RUUVI_URL = " 72 75 75 2E 76 69 2F 23 ";
    protected static final String RUUVI_BEGINS_V2 = "> 04 3E 2A 02 01 03 01 ";
    protected static final String RUUVI_BEGINS_V4 = "> 04 3E 2B 02 01 03 01 ";
    private final boolean v4;

    private String latestMac = null;
    private String latestUrlBeginning = null;

    public DataFormatV2V4(boolean v4) {
        this.v4 = v4;
    }

    protected String getRuuviBegins() {
        if (v4) return RUUVI_BEGINS_V4;
        return RUUVI_BEGINS_V2;
    }

    public void read(String rawLine) {
        if (latestMac == null && latestUrlBeginning == null && rawLine.startsWith(getRuuviBegins())) {
            // line with Ruuvi MAC
            latestMac = RuuviUtils.getMacFromLine(rawLine.substring(getRuuviBegins().length()));
        } else if (latestMac != null && latestUrlBeginning == null && rawLine.contains(RUUVI_URL)) {
            // previous line had a Ruuvi MAC, this has beginning of url
            latestUrlBeginning = getRuuviUrlBeginningFromLine(rawLine);
        } else if (latestMac != null && latestUrlBeginning != null) {
            // this has the remaining part of the url
            try {
                String url = latestUrlBeginning + getRuuviUrlEndingFromLine(rawLine);
                handleMeasurement(latestMac, RuuviUtils.hexToAscii(url));
            } finally {
                latestMac = null;
                latestUrlBeginning = null;
            }
        }
    }

    public void reset() {
        latestMac = null;
        latestUrlBeginning = null;
    }
    
    protected byte[] base64ToByteArray(String base64){
        if (v4) {
            // The extra character alone at the end makes the base64 string to be invalid, discard it
            base64 = base64.substring(0, base64.length() - 1);
        }
        // Ruuvi uses URL-safe Base64, convert that to "traditional" Base64
        return Base64.getDecoder().decode(base64.replace('-', '+').replace('_', '/'));
    }

    private void handleMeasurement(String mac, String base64) {
        byte[] data = base64ToByteArray(base64);
        if (data[0] != 2 && data[0] != 4) {
            // unknown type
            return;
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

        Point point1 = Point.measurement(mac)
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .addField("temperature", temperature)
            .addField("humidity", humidity)
            .addField("pressure", pressure)
            .build();

        Main.influx.write(Config.INFLUX_DATABASENAME, "autogen", point1);
    }

    private String getRuuviUrlBeginningFromLine(String line) {
        return line.substring(line.indexOf(RUUVI_URL) + RUUVI_URL.length());
    }

    private String getRuuviUrlEndingFromLine(String line) {
        line = line.trim();
        return line.substring(0, line.lastIndexOf(' '));
    }
}
