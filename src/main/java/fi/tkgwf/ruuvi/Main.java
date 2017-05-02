package fi.tkgwf.ruuvi;

import fi.tkgwf.ruuvi.handler.impl.DataFormatV2V4;
import fi.tkgwf.ruuvi.handler.impl.DataFormatV3;
import org.apache.log4j.Logger;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Main {
    public static InfluxDB influx;
    private static final Logger LOG = Logger.getLogger(Main.class);
    private static final DataFormatV2V4 format2 = new DataFormatV2V4(false);
    private static final DataFormatV3 format3 = new DataFormatV3();
    private static final DataFormatV2V4 format4 = new DataFormatV2V4(true);

    public static void main(String[] args) throws Exception {
        influx = InfluxDBFactory.connect(Config.INFLUX_URL, Config.INFLUX_USER, Config.INFLUX_PASS);
        influx.createDatabase(Config.INFLUX_DATABASENAME);
        // Flush every 2000 Points, at least every 1s
        influx.enableBatch(2000, 1, TimeUnit.SECONDS);
        Main m = new Main();
        m.run();
    }

    private BufferedReader startHciListeners() throws IOException {
        Process hcitool = new ProcessBuilder("hcitool", "lescan", "--duplicates").start();
        Runtime.getRuntime().addShutdownHook(new Thread(hcitool::destroyForcibly));
        Process hcidump = new ProcessBuilder("hcidump", "--raw").start();
        Runtime.getRuntime().addShutdownHook(new Thread(hcidump::destroyForcibly));
        return new BufferedReader(new InputStreamReader(hcidump.getInputStream()));
    }

    private void run() throws IOException {
        BufferedReader reader = startHciListeners();
        LOG.info("BLE listener started successfully, waiting for data... \nIf you don't get any data, check that you are able to run 'hcitool lescan --duplicates' and 'hcidump --raw' without issues");
        boolean dataReceived = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (!dataReceived) {
                LOG.info("Successfully reading data from hcidump");
                dataReceived = true;
            }
            try {
                format2.read(line);
                format3.read(line);
                format4.read(line);
            } catch (Exception ex) {
                LOG.warn("Uncaught exception while handling measurements", ex);
                format2.reset();
                format3.reset();
                format4.reset();
            }
        }
    }
}
