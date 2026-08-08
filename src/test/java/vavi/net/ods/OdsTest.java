/*
 * Copyright (c) 2021 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.ods;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * The whole round trip, the way a mac does it: find a machine sharing a disc over
 * bonjour, take the address, the port and the disc name out of the announcement, and
 * read the disc over http with nothing else to go on.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2021/12/24 umjammer initial version <br>
 * @see BonjourTest for the announcement itself, {@link OdsServerTest} for the protocol
 */
@Timeout(120)
class OdsTest {

    static final String LABEL = "ODSROUNDTRIP" + (int) (Math.random() * 10000);

    static OdsServer server;

    static int port;

    static byte[] image;

    static JmDNS browser;

    @BeforeAll
    static void beforeAll(@TempDir Path root) throws Exception {
        image = Iso9660.of(LABEL);
        // named after the label it carries, so the disc is announced under the same
        // name whether or not cdrtools is installed to read the label out of it
        Files.write(root.resolve(LABEL + ".iso"), image);

        port = OdsServerTest.freePort();
        server = new OdsServer(root.toString(), port);
        server.start();

        announceAddress = InetAddress.getByName(server.host());
        browser = JmDNS.create(announceAddress);
    }

    /** the interface the server announces on */
    static InetAddress announceAddress;

    @AfterAll
    static void afterAll() throws Exception {
        if (browser != null) {
            browser.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("a disc is found over bonjour and read over http, with nothing known in advance")
    void roundTrip() throws Exception {
        ServiceInfo announced = discover();
        if (announced == null) {
            // a network that cannot carry an announcement says nothing about a
            // server, so skip rather than blame it
            assumeTrue(Mdns.works(announceAddress),
                       "this host cannot see an announcement of its own, multicast does not work here");
        }
        assertNotNull(announced, "nothing announced on port " + port);

        // what the announcement says the disc is called, and where to fetch it
        String disk = Collections.list(announced.getPropertyNames()).stream()
                .filter(name -> name.startsWith("disk"))
                .filter(name -> announced.getPropertyString(name).contains("adVN=" + LABEL))
                .findFirst()
                .orElseThrow();
        String url = "http://" + announced.getInetAddresses()[0].getHostAddress() + ":" +
                     announced.getPort() + "/" + disk + ".dmg";

        HttpClient http = HttpClient.newHttpClient();

        // ask how big the disc is, the way apple's client opens one
        HttpResponse<Void> stat = http.send(HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", ODS.STAT_USER_AGENT)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build(), BodyHandlers.discarding());

        assertEquals(200, stat.statusCode());
        long size = Long.parseLong(stat.headers().firstValue("Content-Length").orElseThrow());
        assertEquals(image.length, size);

        // then read it, a chunk at a time, and put the disc back together
        byte[] read = new byte[(int) size];
        int chunk = 32 * 1024;
        for (int pos = 0; pos < size; pos += chunk) {
            int last = (int) Math.min(pos + chunk, size) - 1;
            HttpResponse<byte[]> data = http.send(HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", ODS.READ_USER_AGENT)
                    .header("Range", "bytes=%d-%d".formatted(pos, last))
                    .GET()
                    .build(), BodyHandlers.ofByteArray());

            assertEquals(200, data.statusCode());
            assertEquals("bytes %d-%d/%d".formatted(pos, last, size),
                         data.headers().firstValue("Content-Range").orElseThrow());
            System.arraycopy(data.body(), 0, read, pos, data.body().length);
        }

        assertArrayEquals(image, read, "the disc did not come back the way it went out");
    }

    @Test
    @DisplayName("the disc the server offers is the image in its directory")
    void shared() throws Exception {
        Map<String, OnlineDisk> disks = server.disks();

        assertEquals(1, disks.size());
        OnlineDisk disk = disks.values().iterator().next();
        assertEquals(image.length, disk.size());
        assertTrue(Arrays.equals(image, Files.readAllBytes(disk.path())));
    }

    /** @return our own announcement, told apart from any other server on this network */
    static ServiceInfo discover() throws IOException {
        long deadline = System.currentTimeMillis() + 20_000;
        do {
            for (ServiceInfo info : browser.list(Bonjour.TYPE, 5_000)) {
                if (info.getPort() == port) {
                    return info;
                }
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }
}
