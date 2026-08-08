/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.ods;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * What a machine browsing for a remote disc sees of this server: an
 * {@code _odisk._tcp} service naming every disc on offer, which is how a mac decides
 * to show it in the finder sidebar.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @see "https://github.com/umjammer/vavi-nio-file-discutils discUtils.opticalDiscSharing, the client side"
 */
@Timeout(120)
class BonjourTest {

    /** a label no other machine on this network is sharing a disc under */
    static final String LABEL = "ODSBONJOUR" + (int) (Math.random() * 10000);

    /** how long to keep browsing: bonjour probes a new service before announcing it */
    static final long BROWSE_TIMEOUT = 20_000;

    static OdsServer server;

    static int port;

    static JmDNS browser;

    /** our announcement, as a machine browsing the network sees it */
    static ServiceInfo announced;

    @BeforeAll
    static void beforeAll(@TempDir Path root) throws Exception {
        // named after the label it carries, so the announcement reads the same
        // whether or not cdrtools is installed to read the label out of the image
        Files.write(root.resolve(LABEL + ".iso"), Iso9660.of(LABEL));

        port = OdsServerTest.freePort();
        server = new OdsServer(root.toString(), port);
        server.start();

        announceAddress = InetAddress.getByName(server.host());
        browser = JmDNS.create(announceAddress);
        announced = browse();
    }

    /** the interface the server announces on */
    static InetAddress announceAddress;

    /**
     * Holds the test to what was announced, unless nothing here could have carried
     * an announcement in the first place - a build machine is often a vm whose
     * network drops multicast, and there the server is not the one at fault.
     */
    static void requireAnnouncement() {
        if (announced == null) {
            assumeTrue(Mdns.works(announceAddress),
                       "this host cannot see an announcement of its own, multicast does not work here");
        }
        assertNotNull(announced, "nothing announced on port " + port);
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (browser != null) {
            browser.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    /** @return our own announcement, told apart from any other server on this network */
    static ServiceInfo browse() throws IOException {
        long deadline = System.currentTimeMillis() + BROWSE_TIMEOUT;
        do {
            for (ServiceInfo info : browser.list(Bonjour.TYPE, 5_000)) {
                if (info.getPort() == port) {
                    return info;
                }
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    @Test
    @DisplayName("the server is announced as a machine sharing an optical disc")
    void announced() {
        requireAnnouncement();
        assertEquals(Bonjour.TYPE, announced.getType());
        assertEquals(port, announced.getPort());
    }

    @Test
    @DisplayName("the announcement carries the flags a remote disc client reads")
    void systemProperties() {
        requireAnnouncement();

        String sys = announced.getPropertyString("sys");
        assertNotNull(sys, "no sys record");
        // adVF is the flags word, and without 0x200 in it a client may just connect
        assertTrue(sys.contains("adVF="), "no adVF in " + sys);
        assertEquals(0, flag(sys, "adVF") & 0x200, "this server asks nobody for permission");
    }

    @Test
    @DisplayName("every disc on offer is announced with its label")
    void discsProperties() {
        requireAnnouncement();

        List<String> names = Collections.list(announced.getPropertyNames());
        String disk = names.stream()
                .filter(name -> name.startsWith("disk"))
                .map(announced::getPropertyString)
                .filter(value -> value.contains("adVN=" + LABEL))
                .findFirst()
                .orElse(null);

        assertNotNull(disk, "the shared disc is not announced: " + names);
        assertTrue(disk.contains("adVT=public.cd-media"), "not announced as a disc: " + disk);
    }

    @Test
    @DisplayName("the announcement points at a host a client can reach")
    void address() {
        requireAnnouncement();

        assertTrue(announced.getInetAddresses().length > 0, "announced with no address");
        assertEquals(server.host(), announced.getInetAddresses()[0].getHostAddress());
    }

    /** @return one hex or decimal flag out of a bonjour parameter record */
    static int flag(String record, String name) {
        for (String pair : record.split(",")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return parts[1].startsWith("0x") ? Integer.parseInt(parts[1].substring(2), 16)
                                                 : Integer.parseInt(parts[1]);
            }
        }
        return 0;
    }
}
