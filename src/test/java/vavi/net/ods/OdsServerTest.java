/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.ods;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * The conversation a remote disc client has with this server: it asks a disc's size
 * with a HEAD and then reads the disc in ranges, calling itself what apple's client
 * calls itself. Anything else is turned away.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 */
@Timeout(60)
class OdsServerTest {

    static final String LABEL = "ODSTEST";

    static OdsServer server;

    /** the image the server shares, byte for byte */
    static byte[] image;

    static HttpClient http;

    static String url;

    /** the name, "disk0" or "disk1", the shared iso ended up announced under */
    static String disk;

    @BeforeAll
    static void beforeAll(@TempDir Path root) throws Exception {
        image = Iso9660.of(LABEL);
        // named after the label it carries: cdrtools reads the label out of the
        // image where it is installed, and the file name stands in where it is not
        Files.write(root.resolve(LABEL + ".iso"), image);
        Files.write(root.resolve("OTHER.img"), Iso9660.of("OTHER"));

        int port = freePort();
        server = new OdsServer(root.toString(), port);
        server.start();

        http = HttpClient.newHttpClient();
        url = "http://127.0.0.1:" + port;
        disk = nameOf(LABEL + ".iso");
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    /** @return a port nothing is listening on, so a test never fights the real server */
    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** @return the disk name the given image is shared as, the disks being numbered */
    static String nameOf(String fileName) throws IOException {
        return server.disks().entrySet().stream()
                .filter(e -> e.getValue().path().getFileName().toString().equals(fileName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    static HttpResponse<byte[]> head(String path, String userAgent) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url + path))
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
        if (userAgent != null) {
            request.header("User-Agent", userAgent);
        }
        return http.send(request.build(), BodyHandlers.ofByteArray());
    }

    static HttpResponse<byte[]> get(String path, String userAgent, String range) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url + path)).GET();
        if (userAgent != null) {
            request.header("User-Agent", userAgent);
        }
        if (range != null) {
            request.header("Range", range);
        }
        return http.send(request.build(), BodyHandlers.ofByteArray());
    }

    @Test
    @DisplayName("both shared images are on offer, numbered")
    void discs() throws Exception {
        Map<String, OnlineDisk> disks = server.disks();

        assertEquals(2, disks.size());
        assertTrue(disks.containsKey("disk0"));
        assertTrue(disks.containsKey("disk1"));
        assertEquals(LABEL, disks.get(disk).label());
        assertEquals(image.length, disks.get(disk).size());
    }

    @Test
    @DisplayName("a HEAD tells the client how big the disc is")
    void size() throws Exception {
        HttpResponse<byte[]> response = head("/" + disk + ".dmg", ODS.STAT_USER_AGENT);

        assertEquals(200, response.statusCode());
        assertEquals(String.valueOf(image.length),
                     response.headers().firstValue("Content-Length").orElseThrow());
        assertEquals("bytes", response.headers().firstValue("Accept-Ranges").orElseThrow());
    }

    @Test
    @DisplayName("a range comes back byte for byte, and says what it is a range of")
    void read() throws Exception {
        HttpResponse<byte[]> response = get("/" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=0-15");

        assertEquals(200, response.statusCode());
        assertArrayEquals(Arrays.copyOfRange(image, 0, 16), response.body());
        assertEquals("bytes 0-15/" + image.length,
                     response.headers().firstValue("Content-Range").orElseThrow());
    }

    @Test
    @DisplayName("a range out of the middle is the middle of the image")
    void readInside() throws Exception {
        // the primary volume descriptor, i.e. where the disc says what it is called
        int start = 16 * Iso9660.SECTOR_SIZE;
        HttpResponse<byte[]> response =
                get("/" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=%d-%d".formatted(start, start + 2047));

        assertEquals(200, response.statusCode());
        assertArrayEquals(Arrays.copyOfRange(image, start, start + 2048), response.body());
    }

    @Test
    @DisplayName("an open ended range reads to the end of the disc")
    void readToEnd() throws Exception {
        int start = image.length - 16;
        HttpResponse<byte[]> response = get("/" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=%d-".formatted(start));

        assertEquals(200, response.statusCode());
        assertArrayEquals(Arrays.copyOfRange(image, start, image.length), response.body());
    }

    @Test
    @DisplayName("the last byte of the disc is readable")
    void readLastByte() throws Exception {
        int last = image.length - 1;
        HttpResponse<byte[]> response =
                get("/" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=%d-%d".formatted(last, last));

        assertEquals(200, response.statusCode());
        assertArrayEquals(new byte[] {image[last]}, response.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {".dmg", ".iso", ".img"})
    @DisplayName("a disc answers by whichever extension it is asked for")
    void extensions(String extension) throws Exception {
        HttpResponse<byte[]> response = get("/" + disk + extension, ODS.READ_USER_AGENT, "bytes=0-15");

        assertEquals(200, response.statusCode());
        assertArrayEquals(Arrays.copyOfRange(image, 0, 16), response.body());
    }

    @Test
    @DisplayName("a disc also answers when it is named in the query, the way finder asks")
    void asQuery() throws Exception {
        HttpResponse<byte[]> response = get("/?disk=" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=0-15");

        assertEquals(200, response.statusCode());
        assertArrayEquals(Arrays.copyOfRange(image, 0, 16), response.body());
    }

    @Test
    @DisplayName("only apple's client is served")
    void otherClients() throws Exception {
        assertEquals(403, get("/" + disk + ".dmg", "curl/8.7.1", "bytes=0-15").statusCode());
        assertEquals(403, head("/" + disk + ".dmg", "curl/8.7.1").statusCode());
        // reading with the user agent that only asks for a size is no better
        assertEquals(403, get("/" + disk + ".dmg", ODS.STAT_USER_AGENT, "bytes=0-15").statusCode());
    }

    @Test
    @DisplayName("a client that says nothing about itself is turned away, not crashed into")
    void noUserAgent() throws Exception {
        assertEquals(403, get("/" + disk + ".dmg", null, "bytes=0-15").statusCode());
        assertEquals(403, head("/" + disk + ".dmg", null).statusCode());
    }

    @Test
    @DisplayName("a disc nobody shares is not found")
    void unknownDisc() throws Exception {
        assertEquals(404, get("/disk9.dmg", ODS.READ_USER_AGENT, "bytes=0-15").statusCode());
        assertEquals(404, head("/disk9.dmg", ODS.STAT_USER_AGENT).statusCode());
    }

    @Test
    @DisplayName("a read with no range at all is a bad request")
    void noRange() throws Exception {
        assertEquals(400, get("/" + disk + ".dmg", ODS.READ_USER_AGENT, null).statusCode());
    }

    @Test
    @DisplayName("a range the disc does not reach is not satisfiable")
    void rangeOutOfBounds() throws Exception {
        assertEquals(416, get("/" + disk + ".dmg", ODS.READ_USER_AGENT,
                              "bytes=%d-%d".formatted(image.length, image.length + 15)).statusCode());
        assertEquals(416, get("/" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=100-10").statusCode());
    }

    @Test
    @DisplayName("a range that runs off the end stops at the end")
    void rangePastTheEnd() throws Exception {
        int start = image.length - 16;
        HttpResponse<byte[]> response =
                get("/" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=%d-%d".formatted(start, image.length + 1000));

        assertEquals(200, response.statusCode());
        assertArrayEquals(Arrays.copyOfRange(image, start, image.length), response.body());
    }

    @Test
    @DisplayName("the server names itself the way a remote disc server does")
    void serverHeader() throws Exception {
        HttpResponse<byte[]> response = get("/" + disk + ".dmg", ODS.READ_USER_AGENT, "bytes=0-15");

        assertEquals("ODS/1.0", response.headers().firstValue("Server").orElseThrow());
        assertEquals("application/octet-stream", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    @DisplayName("the shared directory is what the server serves from")
    void root() {
        assertNotNull(server.root());
        assertTrue(server.port() > 0);
    }
}
