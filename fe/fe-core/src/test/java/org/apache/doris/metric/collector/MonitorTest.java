package org.apache.doris.metric.collector;

import com.clearspring.analytics.util.Lists;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.doris.catalog.Catalog;
import org.apache.doris.common.Config;
import org.apache.doris.common.DdlException;
import org.apache.doris.ha.HAProtocol;
import org.apache.doris.persist.gson.GsonUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class MonitorTest {
    private static final String node1 = "xafj-palo-rpm51.xafj.baidu.com:8080";
    private static final String node2 = "xafj-palo-rpm52.xafj.baidu.com:8080";
    private Type typeListLong = new TypeToken<List<Long>>() {
    }.getType();
    private Type typeListDouble = new TypeToken<List<Double>>() {
    }.getType();
    private long startTime = 1614587373000L;
    private long endTime = 1614590973000L;

    @Mocked
    private BDBJEMetricHandler bdbjeMetricHandler;
    @Mocked
    private Catalog catalog;
    @Mocked
    private HAProtocol haProtocol;

    private void setUp() throws UnsupportedEncodingException, UnknownHostException {
        Config.enable_monitor = true;
        Config.http_port = 8080;
        new Expectations() {
            {
                Catalog.getCurrentCatalog();
                minTimes = 0;
                result = catalog;

                catalog.getBDBJEMetricHandler();
                minTimes = 0;
                result = bdbjeMetricHandler;

                catalog.getHaProtocol();
                minTimes = 0;
                result = haProtocol;

                InetAddress inetAddress = InetAddress.getByName("xafj-palo-rpm51.xafj.baidu.com");
                InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 8080);
                haProtocol.getLeader();
                minTimes = 0;
                result = inetSocketAddress;

                bdbjeMetricHandler.readLong(anyString);
                minTimes = 0;
                result = new Delegate() {
                    long fakeReadLong(String key) {
                        String[] strings = key.split("_");
                        return Long.parseLong(strings[strings.length - 1]);
                    }
                };

                bdbjeMetricHandler.readDouble(anyString);
                minTimes = 0;
                result = new Delegate() {
                    double fakeReadDouble(String key) {
                        String[] strings = key.split("_");
                        return Double.parseDouble(strings[strings.length - 1]);
                    }
                };
            }
        };
    }

    static class Para {
        @SerializedName(value = "nodes")
        private List<String> nodes;
        @SerializedName(value = "point_num")
        private int point_num;

        public Para(List<String> nodes, int points) {
            this.nodes = nodes;
            this.point_num = points;
        }
    }

    private Object testMonitor(Monitor.MonitorType monitorType) throws DdlException {
        List<String> nodes = Lists.newArrayList();
        nodes.add(node1);
        nodes.add(node2);
        String nodesJson = GsonUtils.GSON.toJson(new Para(nodes, 2));
        return Monitor.monitoring(startTime, endTime, nodesJson, monitorType);
    }

    private long parseJsonLong(Object object) throws DdlException {
        if (object instanceof Monitor.ChartData) {
            Monitor.ChartData chartData = (Monitor.ChartData) object;
            List<Long> list = (List<Long>) chartData.y_value.get(node1);
            return list.get(0);
        }
        throw new DdlException("");
    }

    private double parseJsonDouble(Object object) throws DdlException {
        if (object instanceof Monitor.ChartData) {
            Monitor.ChartData chartData = (Monitor.ChartData) object;
            List<Double> list = (List<Double>) chartData.y_value.get(node1);
            return list.get(0);
        }
        throw new DdlException("");
    }

    @Test
    public void testQps() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.QPS);
        double result = parseJsonDouble(resultJson);
        Assert.assertTrue(startTime <= result && result <= endTime);
    }

    @Test
    public void testQueryErrRate() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.QUERY_ERR_RATE);
        double result = parseJsonDouble(resultJson);
        Assert.assertTrue(startTime <= result && result <= endTime);
    }

    @Test
    public void testConnectionTotal() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.CONN_TOTAL);
        long result = parseJsonLong(resultJson);
        Assert.assertTrue(startTime <= result && endTime >= result);
    }

    @Test
    public void testTxnStatus() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.TXN_STATUS);
        Monitor.ChartDataTxn chartData = (Monitor.ChartDataTxn) resultJson;
        Map<String, List<Double>> map = (Map<String, List<Double>>) chartData.y_value.get(node1);
        double result = map.get("begin").get(0);
        Assert.assertEquals(1000.0, result, 0.00001);
    }

    @Test
    public void testBeBaseCompactionScore() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.BE_BASE_COMPACTION_SCORE);
        long result = parseJsonLong(resultJson);
        Assert.assertTrue(startTime <= result && endTime >= result);
    }

    @Test
    public void testBeCumuCompactionScore() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.BE_CUMU_COMPACTION_SCORE);
        long result = parseJsonLong(resultJson);
        Assert.assertTrue(startTime <= result && endTime >= result);
    }

    @Test
    public void testBeCpuIdle() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.BE_CPU_IDLE);
        Assert.assertEquals(100L, parseJsonLong(resultJson));
    }

    @Test
    public void testBeMem() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.BE_MEM);
        long result = parseJsonLong(resultJson);
        Assert.assertTrue(startTime <= result && endTime >= result);
    }

    @Test
    public void testDiskIoUtil() throws Exception {
        setUp();
        Object resultJson = testMonitor(Monitor.MonitorType.BE_DISK_IO);
        long result = parseJsonLong(resultJson);
        Assert.assertTrue(startTime <= result && endTime >= result);
    }
}
