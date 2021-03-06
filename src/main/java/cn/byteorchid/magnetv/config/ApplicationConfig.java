package cn.byteorchid.magnetv.config;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

@Configuration
@PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
public class ApplicationConfig {
    private Logger logger = Logger.getLogger(getClass());

    @Value("${rule.json.uri}")
    public String ruleJsonUri;

    @Value("${admin.password.md5}")
    public String adminPasswordMD5;

    @Value("${project.version}")
    public String versionName;

    @Value("${request.source.timeout}")
    public long sourceTimeout;

    @Value("${proxy.ignore}")
    public boolean proxyIgnore;

    @Value("${proxy.enabled}")
    public boolean proxyEnabled;

    @Value("${proxy.type}")
    public String proxyTypeSting;

    @Value("${proxy.host}")
    public String proxyHost;

    @Value("${proxy.port}")
    public int proxyPort;

    @Value("${result.toast}")
    public boolean resultToast;

    @Value("${search.placeholder}")
    public String searchPlaceholder;

    @Value("${version.link}")
    public String versionLink;

    @Value("${search.report.enabled}")
    public boolean reportEnabled;

    @Value("${preload.enabled}")
    public boolean preloadEnabled;

    @Value("${aria2.rpc}")
    public String aria2Rpc;

    @Value("${aria2.rpc.token}")
    public String aria2RpcToken;

    @Value("${trackers.enabled}")
    public boolean trackersEnabled;

    @Value("${trackers.update.url}")
    public String trackersUrl;

    @Value("${trackers.update.interval.hour}")
    public int trackersUpdateIntervalHour;

    @PostConstruct
    public void onInit() {
        //????????????????????? ??????????????????
        try {
            File configFile = getExternalConfigFile();
            if (configFile.exists()) {
                StringBuffer sb = new StringBuffer();
                sb.append("???????????????????????????????????????????????????...\n");
                Properties properties = PropertiesLoaderUtils.loadProperties(new FileSystemResource(configFile));
                Field[] fields = ApplicationConfig.class.getFields();
                for (Field field : fields) {
                    Value valueAnnotation = field.getAnnotation(Value.class);
                    if (valueAnnotation != null) {
                        Matcher matcher = Pattern.compile("(?<=\\{).+?(?=\\})").matcher(valueAnnotation.value());
                        if (matcher.find()) {
                            //???????????????????????????
                            String key = matcher.group();
                            if (properties.containsKey(key)) {
                                String value = properties.getProperty(key);
                                if (value != null) {
                                    sb.append("????????????--->");
                                    sb.append(key);
                                    sb.append("=");
                                    sb.append(value);
                                    sb.append("\n");
                                    if (field.getType() == long.class) {
                                        field.setLong(this, Long.parseLong(value));
                                    } else if (field.getType() == int.class) {
                                        field.setInt(this, Integer.parseInt(value));
                                    } else if (field.getType() == float.class) {
                                        field.setFloat(this, Float.parseFloat(value));
                                    } else if (field.getType() == double.class) {
                                        field.setDouble(this, Double.parseDouble(value));
                                    } else if (field.getType() == boolean.class) {
                                        field.setBoolean(this, Boolean.parseBoolean(value));
                                    } else {
                                        field.set(this, value);
                                    }
                                }
                            }
                        }
                    }
                }
                logger.info(sb.toString());
            }
        } catch (Exception e) {
            logger.error("??????????????????????????????", e);
        }
    }

    /**
     * ??????json??????????????????
     */
    public boolean isLocalRule() {
        return ruleJsonUri != null && !ruleJsonUri.startsWith("http");
    }

    /**
     * ?????????????????? ????????????????????????
     *
     * @return
     */
    public File getExternalDataDir() {
        File rootParent = new File(getClass().getResource("/").getPath()).getParentFile().getParentFile().getParentFile();
        File dir = new File(rootParent, "magnetv-data");
        //??????????????????????????????
        if (!dir.exists()) {
            rootParent.setWritable(true, false);
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * ??????????????????
     *
     * @return
     */
    public File getExternalConfigFile() {
        return new File(getExternalDataDir(), "application.properties");
    }

}
