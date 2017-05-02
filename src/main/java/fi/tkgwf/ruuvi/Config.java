package fi.tkgwf.ruuvi;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * @author Teemu Kanstren
 */
public class Config {
    public static String INFLUX_URL;
    public static String INFLUX_USER;
    public static String INFLUX_PASS;
    public static String INFLUX_DATABASENAME;

    public static final String CONFIG_FILENAME = "ruuvi-collector.properties";

    static {
        try {
            Properties props = new Properties();
            File file = new File(CONFIG_FILENAME);
            if (!file.exists()) {
                file = new File("../"+CONFIG_FILENAME);
            }
            FileInputStream stream = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(stream, "UTF-8");
            props.load(isr);
            INFLUX_URL = parseString("influx.url", props);
            INFLUX_USER = parseString("influx.user", props);
            INFLUX_PASS = parseString("influx.pass", props);
            INFLUX_DATABASENAME = parseString("influx.databasename", props);
        } catch (Exception e) {
            System.err.println("Unable to load configuration");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static String parseString(String key, Properties props) {
        String value = props.getProperty(key);
        if (value == null)
            throw new IllegalArgumentException("Missing configuration key: " + key + " in " + CONFIG_FILENAME);
        return value;
    }
}
