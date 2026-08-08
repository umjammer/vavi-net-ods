/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.ods;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;


/**
 * The smallest thing cdrtools still calls an iso: a system area, a primary volume
 * descriptor carrying the volume label, a terminator and an empty root directory.
 * Enough for a test to hand the server a disc image with a label on it, without
 * asking mkisofs to make one.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 */
final class Iso9660 {

    static final int SECTOR_SIZE = 2048;

    /**
     * 16 sectors of system area, the descriptors, the path tables and the root, and
     * then room to spare - isoinfo reads past the end of a very small image and
     * gives up with "Short read on old image" rather than report what it found.
     */
    static final int SECTORS = 64;

    private Iso9660() {
    }

    /**
     * @param volumeLabel the volume id to write, what a client sees as the disc name
     * @return the image
     */
    static byte[] of(String volumeLabel) {
        byte[] image = new byte[SECTORS * SECTOR_SIZE];

        int pvd = 16 * SECTOR_SIZE;
        image[pvd] = 1; // primary volume descriptor
        header(image, pvd);
        strD(image, pvd + 8, "ODS TEST", 32); // system id
        strD(image, pvd + 40, volumeLabel, 32); // volume id
        both32(image, pvd + 80, SECTORS); // volume space size, in sectors
        both16(image, pvd + 120, 1); // volume set size
        both16(image, pvd + 124, 1); // volume sequence number
        both16(image, pvd + 128, SECTOR_SIZE); // logical block size
        both32(image, pvd + 132, 10); // path table size
        le32(image, pvd + 140, 19); // location of the l path table
        be32(image, pvd + 148, 20); // location of the m path table
        rootDirectory(image, pvd + 156);
        strD(image, pvd + 190, "", 128); // volume set id
        strD(image, pvd + 318, "", 128); // publisher id
        strD(image, pvd + 446, "", 128); // data preparer id
        strD(image, pvd + 574, "", 128); // application id
        image[pvd + 881] = 1; // file structure version

        int terminator = 17 * SECTOR_SIZE;
        image[terminator] = (byte) 255;
        header(image, terminator);

        // the l and m path tables, one entry for the root
        pathTable(image, 19 * SECTOR_SIZE, true);
        pathTable(image, 20 * SECTOR_SIZE, false);

        // the root directory itself, holding "." and ".."
        rootDirectory(image, 21 * SECTOR_SIZE);
        rootDirectory(image, 21 * SECTOR_SIZE + 34);
        image[21 * SECTOR_SIZE + 34 + 32] = 1;
        image[21 * SECTOR_SIZE + 34 + 33] = 1; // ".." names itself with 0x01

        return image;
    }

    /** the "CD001" identifier and version every descriptor starts with */
    private static void header(byte[] image, int offset) {
        System.arraycopy("CD001".getBytes(StandardCharsets.US_ASCII), 0, image, offset + 1, 5);
        image[offset + 6] = 1;
    }

    private static void rootDirectory(byte[] image, int offset) {
        image[offset] = 34; // the length of this record
        both32(image, offset + 2, 21); // where the root directory lives
        both32(image, offset + 10, SECTOR_SIZE); // and how long it is
        image[offset + 25] = 0x02; // it is a directory
        both16(image, offset + 28, 1); // volume sequence number
        image[offset + 32] = 1; // one character of name, 0x00, i.e. "."
    }

    private static void pathTable(byte[] image, int offset, boolean littleEndian) {
        image[offset] = 1; // one character of name
        if (littleEndian) {
            le32(image, offset + 2, 21);
            image[offset + 6] = 1;
        } else {
            be32(image, offset + 2, 21);
            image[offset + 7] = 1;
        }
    }

    /** a d-character field, blank padded the way iso 9660 wants it */
    private static void strD(byte[] image, int offset, String value, int length) {
        Arrays.fill(image, offset, offset + length, (byte) ' ');
        byte[] bytes = value.toUpperCase().getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, image, offset, Math.min(bytes.length, length));
    }

    /** iso 9660 writes its numbers twice, once each way round */
    private static void both32(byte[] image, int offset, int value) {
        le32(image, offset, value);
        be32(image, offset + 4, value);
    }

    private static void both16(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
        image[offset + 1] = (byte) (value >> 8);
        image[offset + 2] = (byte) (value >> 8);
        image[offset + 3] = (byte) value;
    }

    private static void le32(byte[] image, int offset, int value) {
        for (int i = 0; i < 4; i++) {
            image[offset + i] = (byte) (value >> (8 * i));
        }
    }

    private static void be32(byte[] image, int offset, int value) {
        for (int i = 0; i < 4; i++) {
            image[offset + i] = (byte) (value >> (8 * (3 - i)));
        }
    }
}
