/*
 * Copyright (c) 2021 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.ods;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import vavi.util.Debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * OdsTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2021/12/24 umjammer initial version <br>
 */
class OdsTest {

    static {
        System.setProperty("vavi.util.logging.VaviFormatter.extraClassMethod",
                "(" +
                "org\\.slf4j\\.impl\\.JDK14LoggerAdapter#.+" +
                "|" +
                "sun\\.util\\.logging\\.LoggingSupport#log" +
                "|" +
                "sun\\.util\\.logging\\.PlatformLogger#fine" +
                "|" +
                "jdk\\.internal\\.event\\.EventHelper#logX509CertificateEvent" +
                "|" +
                "sun\\.util\\.logging\\.PlatformLogger.JavaLoggerProxy#doLog" +
                ")");
    }

    static String mountPoint;

    @BeforeAll
    static void setup() {
        mountPoint = System.getProperty("vavi.net.ods.OdsServer.mountPoint");
Debug.println("mountPoint: " + mountPoint);
    }

    @Test
    @Disabled
    void test0() throws Exception {
        OdsServer server = new OdsServer();
        server.run();
//        server.stop();
    }

    @Test
    void test11() throws Exception {
        Tools tools = Tools.getInstance();
        assertEquals("iso", tools.getExt("aaa.iso"));
        assertTrue(tools.is_image(Paths.get("aaa.iso")));
        assertTrue(tools.is_image(Paths.get("vvv/aaa.dmg")));
        assertTrue(tools.is_image(Paths.get("ccc/dddd/aaa.img")));
        assertFalse(tools.is_image(Paths.get("aaa/bbb")));
    }

    @Test
    void test1() throws Exception {
        Tools tools = Tools.getInstance();
Debug.println("local ip:" + tools.getLocalIp());
tools.listImages(mountPoint).forEach(Debug::println);
        assertTrue(tools.listImages(mountPoint).size() > 0);
        Path image = tools.listImages(mountPoint).get(0);
Debug.println("image: " + image);
Debug.println("label: " + tools.getLabel(image));
        int[] bs = tools.blockSize(image);
Debug.println("bsize: " + bs[0] + ", " + bs[1]);
    }

    @Test
    void test2() throws Exception {
        Tools tools = Tools.getInstance();
        tools.listOpticalDrives();
        //tools.state(Paths.get(moutPoint));
    }
}

/* */
