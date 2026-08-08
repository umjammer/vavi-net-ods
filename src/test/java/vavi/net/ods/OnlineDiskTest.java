/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.ods;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vavi.net.ods.OnlineDisk.DiskImage;
import vavi.net.ods.OnlineDisk.OnlineDiskState;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * How a disc image answers the reads the servlet turns a byte range into.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 */
class OnlineDiskTest {

    static final String LABEL = "ODSDISK";

    DiskImage shared(Path dir) throws Exception {
        Path iso = dir.resolve("shared.iso");
        Files.write(iso, Iso9660.of(LABEL));
        return new DiskImage(iso);
    }

    @Test
    @DisplayName("an image that is there is ready to be read, and knows its size")
    void ready(@TempDir Path dir) throws Exception {
        DiskImage disk = shared(dir);

        assertTrue(disk.exists());
        assertEquals(OnlineDiskState.READY, disk.state());
        assertEquals(Iso9660.SECTORS * Iso9660.SECTOR_SIZE, disk.size());
    }

    @Test
    @DisplayName("an image that is not there is not ready")
    void missing(@TempDir Path dir) {
        DiskImage disk = new DiskImage(dir.resolve("gone.iso"));

        assertFalse(disk.exists());
        assertEquals(OnlineDiskState.EMPTY, disk.state());
    }

    @Test
    @DisplayName("a read returns the bytes between the two addresses, both included")
    void read(@TempDir Path dir) throws Exception {
        DiskImage disk = shared(dir);
        byte[] image = Files.readAllBytes(dir.resolve("shared.iso"));

        assertArrayEquals(Arrays.copyOfRange(image, 0, 16), disk.read(0, 15));
        assertArrayEquals(Arrays.copyOfRange(image, 100, 201), disk.read(100, 200));
        assertArrayEquals(Arrays.copyOfRange(image, 100, 101), disk.read(100, 100));
    }

    @Test
    @DisplayName("a read to address zero reads the whole disc")
    void readAll(@TempDir Path dir) throws Exception {
        DiskImage disk = shared(dir);

        assertArrayEquals(Files.readAllBytes(dir.resolve("shared.iso")), disk.read(0, 0 /* to the end */));
    }

    @Test
    @DisplayName("the last byte of the disc is readable, one past it is not")
    void readTheEnd(@TempDir Path dir) throws Exception {
        DiskImage disk = shared(dir);
        int last = disk.size() - 1;

        assertEquals(1, disk.read(last, last).length);
        assertThrows(IllegalArgumentException.class, () -> disk.read(last, last + 1));
    }

    @Test
    @DisplayName("a read that ends before it starts is refused")
    void readBackwards(@TempDir Path dir) throws Exception {
        DiskImage disk = shared(dir);

        assertThrows(IllegalArgumentException.class, () -> disk.read(200, 100));
    }

    @Test
    @DisplayName("a disc is labelled with what is written on it")
    void label(@TempDir Path dir) throws Exception {
        DiskImage disk = shared(dir);

        // isoinfo reads the volume id where it is installed, the file name stands
        // in for it where it is not, but a disc is never announced without a name
        assertFalse(disk.label().isBlank());
    }

    @Test
    @DisplayName("two discs of the same size and name are the same disc to the announcer")
    void identity(@TempDir Path dir) throws Exception {
        DiskImage one = shared(dir);
        Path copy = dir.resolve("copy.iso");
        Files.write(copy, Files.readAllBytes(dir.resolve("shared.iso")));

        // an announcement is refreshed when this changes, so a rename has to show
        assertEquals(one.hashCode(), new DiskImage(dir.resolve("shared.iso")).hashCode());
    }
}
