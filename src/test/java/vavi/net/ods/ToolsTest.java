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
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * What {@link Tools} makes of a path and of the output of the cdrtools it drives.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 */
class ToolsTest {

    Tools tools;

    @BeforeEach
    void beforeEach() {
        tools = Tools.getInstance();
    }

    @ParameterizedTest
    @CsvSource({
        "aaa.iso, iso",
        "a.b.dmg, dmg",
        "/some/where/aaa.ISO, ISO",
        "archive.tar.gz, gz",
    })
    @DisplayName("an extension is what follows the last dot")
    void extension(String name, String expected) {
        assertEquals(expected, tools.getExt(name));
    }

    @Test
    @DisplayName("a name with no dot has no extension")
    void noExtension() {
        assertNull(tools.getExt("aaa"));
        assertNull(tools.getExt("bbb/ccc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaa.iso", "vvv/aaa.dmg", "ccc/dddd/aaa.img"})
    @DisplayName("iso, dmg and img are the images this serves")
    void images(String path) {
        assertTrue(tools.is_image(Paths.get(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaa/bbb", "aaa.txt", "aaa.iso.txt", "readme"})
    @DisplayName("anything else is not an image")
    void notImages(String path) {
        assertFalse(tools.is_image(Paths.get(path)));
    }

    @Test
    @DisplayName("only the images in a directory are listed")
    void listImages(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("one.iso"), new byte[1]);
        Files.write(dir.resolve("two.dmg"), new byte[1]);
        Files.write(dir.resolve("notes.txt"), new byte[1]);
        Files.createDirectory(dir.resolve("sub"));

        List<Path> images = tools.listImages(dir.toString());

        assertEquals(2, images.size());
        assertTrue(images.stream().anyMatch(p -> p.getFileName().toString().equals("one.iso")));
        assertTrue(images.stream().anyMatch(p -> p.getFileName().toString().equals("two.dmg")));
    }

    @Test
    @DisplayName("an empty directory shares nothing")
    void listNothing(@TempDir Path dir) throws Exception {
        assertTrue(tools.listImages(dir.toString()).isEmpty());
    }

    @Test
    @DisplayName("a file cdrtools cannot read is labelled with its name")
    void labelOfSomethingElse(@TempDir Path dir) throws Exception {
        Path notAnImage = dir.resolve("HOLIDAY.iso");
        Files.write(notAnImage, "not an iso at all".getBytes(US_ASCII));

        // no volume descriptor to read a label out of, and no cdrtools on every
        // machine either - the disc still has to be announced under some name
        assertEquals("HOLIDAY", tools.getLabel(notAnImage));
    }

    @Test
    @DisplayName("the volume label of a real iso is read out of it")
    void labelOfAnIso(@TempDir Path dir) throws Exception {
        Path iso = dir.resolve("image.iso");
        Files.write(iso, Iso9660.of("ODSLABEL"));
        assumeTrue(cdrtoolsReads(iso), "cdrtools is not installed");

        assertEquals("ODSLABEL", tools.getLabel(iso));
    }

    @Test
    @DisplayName("the geometry of a real iso is read out of it")
    void blockSizeOfAnIso(@TempDir Path dir) throws Exception {
        Path iso = dir.resolve("image.iso");
        Files.write(iso, Iso9660.of("ODSLABEL"));
        assumeTrue(cdrtoolsReads(iso), "cdrtools is not installed");

        int[] geometry = tools.blockSize(iso);

        assertEquals(Iso9660.SECTOR_SIZE, geometry[0]);
        assertEquals(Iso9660.SECTORS, geometry[1]);
    }

    /** @return whether the cdrtools this shells out to are installed */
    private boolean cdrtoolsReads(Path iso) {
        try {
            tools.blockSize(iso);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Test
    @DisplayName("the mac drive list is read for its device names")
    void macDrives() {
        Tools.MacTools mac = new Tools.MacTools();

        assertEquals("IOBDServices/0", mac.getDev("\tName: IOBDServices/0"));
        assertEquals("IODVDServices", mac.getDev("   Name: IODVDServices"));
        assertNull(mac.getDev(" Vendor: MATSHITA"));
        assertNull(mac.getDev(""));
    }

    @Test
    @DisplayName("the linux drive list is read for its device names")
    void linuxDrives() {
        Tools.LinuxTools linux = new Tools.LinuxTools();

        assertEquals("/dev/sr0", linux.getDev("\t0  dev='/dev/sr0'  rwrw-- : 'HL-DT-ST' 'DVDRAM GH24NSD1'"));
        assertNull(linux.getDev("wodim: Overview of accessible drives (1 found) :"));
    }

    @Test
    @DisplayName("there is always an address to announce")
    void localIp() {
        InetAddress address = tools.getLocalIp();

        assertNotNull(address);
        assertFalse(address.isAnyLocalAddress());
    }
}
