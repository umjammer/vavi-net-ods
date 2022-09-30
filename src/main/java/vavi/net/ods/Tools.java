/*
 * https://github.com/klattimer/pyods
 */

package vavi.net.ods;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import vavi.net.ods.OnlineDisk.OnlineDiskState;


public abstract class Tools {

    public static Tools getInstance() {
        String os = System.getProperty("os.name");
        if (os.startsWith("Mac")) {
            return new MacTools();
        } else {
            return new LinuxTools();
        }
    }

    public InetAddress getLocalIp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("google.com", 80));
            return socket.getLocalAddress();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getExt(String filename) {
        if (filename.lastIndexOf('.') == -1) {
            return null;
        }

        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    public boolean is_image(Path path) {
        String ext = getExt(path.getFileName().toString());
        if (ext == null) {
            return false;
        } else {
            return Arrays.asList("iso", "img", "dmg").contains(ext);
        }
    }

    /**
     * Retrieves disc image files.
     */
    public List<Path> listImages(String path) throws IOException {
        return Files.list(Paths.get(path)).filter(this::is_image).collect(Collectors.toList());
    }

    /**
     * Retrieves real optical discs.
     */
    public abstract List<Path> listOpticalDrives() throws IOException;

    /**
     * Retrieves removable discs.
     */
    public abstract List<Path> listRemovableDrives() throws IOException;

    public abstract String getLabel(Path path) throws IOException;

    public abstract int[] blockSize(Path path) throws IOException;

    /** for real optical disk */
    public abstract OnlineDiskState state(Path path) throws IOException;

    /** mac commands */
    static class MacTools extends Tools {
        @Override
        public List<Path> listRemovableDrives() throws IOException {
            return Collections.emptyList(); // TODO
        }

        protected String getDev(String line) {
            final Pattern p = Pattern.compile("Name: (.*?)");
            Matcher m = p.matcher(line);
            return m.find() ? m.find(1) ? m.group(0) : null : null;
        }

        @Override
        public List<Path> listOpticalDrives() throws IOException {

            List<Path> drives = new ArrayList<>();

            for (String l : exec("drutil", "list")) {
                String d = getDev(l);
                if (d != null && !d.isEmpty()) {
                    drives.add(Paths.get(d));
                }
            }

            Collections.sort(drives);
            return drives;
        }

        @Override
        public String getLabel(Path path) throws IOException {
            List<String> out = Tools.exec("/usr/local/bin/isoinfo", "-d", "-i", path.toString());

            for (String line : out) {
                if (line.startsWith("Volume id:") && line.length() > 11) {
                    return line.substring(11);
                }
            }

            return null;
        }

        @Override
        public int[] blockSize(Path path) throws IOException {
            List<String> out = Tools.exec("/usr/local/bin/isoinfo", "-d", "-i", path.toString());

            int blockSize = 0, volSize = 0;
            for (String line : out) {
                if (line.startsWith("Volume size is:") && line.length() > 16) {
                    volSize = Integer.parseInt(line.substring(16));
                }

                if (line.startsWith("Logical block size is:") && line.length() > 23) {
                    blockSize = Integer.parseInt(line.substring(23));
                }
            }
            return new int[] { blockSize, volSize };
        }

        @Override
        public OnlineDiskState state(Path path) throws IOException {
            OnlineDiskState state = OnlineDiskState.READY;
            return state;
        }
    }

    /** */
    static class LinuxTools extends Tools {
        @Override
        public List<Path> listRemovableDrives() {
            @SuppressWarnings("unused")
            String script = "grep -Hv ^0$ /sys/block/*/removable |" +
                    "sed s/removable:.*$/device\\/uevent/ |" +
                    "xargs grep -H ^DRIVER=sd |" +
                    "sed s/device.uevent.*$/size/ |" +
                    "xargs grep -Hv ^0$ |" +
                    "cut -d / -f 4";
            return Collections.emptyList(); // TODO
        }

        protected String getDev(String line) {
            final Pattern p = Pattern.compile("dev='(.*?)'");
            Matcher m = p.matcher(line);
            return m.find(1) ? m.group(0) : null;
        }

        @Override
        public List<Path> listOpticalDrives() throws IOException {

            List<Path> drives = new ArrayList<>();

            for (String l : exec("wodim", "--devices")) {
                String d = getDev(l);
                if (d != null && !d.isEmpty()) {
                    drives.add(Paths.get(d));
                }
            }

            Collections.sort(drives);
            return drives;
        }

        @Override
        public String getLabel(Path path) throws IOException {
            List<String> out = Tools.exec("isoinfo", "-d", "-i", path.toString());

            for (String line : out) {
                if (line.startsWith("volume id:") && line.length() > 11) {
                    return line.substring(11);
                }
            }

            return null;
        }

        @Override
        public int[] blockSize(Path path) throws IOException {
            List<String> out = Tools.exec("isoinfo", "-d", "-i", path.toString());

            int block_size = 0, vol_size = 0;
            for (String line : out) {
                if (line.startsWith("volume size is:") && line.length() > 16) {
                    vol_size = Integer.parseInt(line.substring(16));
                }

                if (line.startsWith("logical block size is:") && line.length() > 23) {
                    block_size = Integer.parseInt(line.substring(23));
                }
            }
            return new int[] { block_size, vol_size };
        }

        @Override
        public OnlineDiskState state(Path path) throws IOException {
            final Pattern typeRe = Pattern.compile("type (\\d*?)");
            @SuppressWarnings("unused")
            int type;
            OnlineDiskState state = OnlineDiskState.NOT_READY;
            String output = Tools.exec("setcd", "-i", path.toString()).get(0);

            if (output.contains("is open")) {
                state = OnlineDiskState.OPEN;
            }
            if (output.contains("not ready")) {
                state = OnlineDiskState.NOT_READY;
            }

            if (output.contains("disc found")) {
                // "Disc found in drive: data disc type 1";
                Matcher m = typeRe.matcher(output);
                type = Integer.parseInt(m.group(1));

                // TODO Extract type
                state = OnlineDiskState.READY;
            }

            if (output.contains("no disc")) {
                state = OnlineDiskState.EMPTY;
            }

            return state;
        }
    }

    /** executes command line */
    protected static List<String> exec(String... commandLine) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
System.out.println("$ " + String.join(" ", commandLine));
        Process process = processBuilder.start();
        Scanner s = new Scanner(process.getInputStream());
        List<String> results = new ArrayList<>();
        while (s.hasNextLine()) {
            results.add(s.nextLine());
System.out.println(results.get(results.size() - 1));
        }
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        s.close();

        int returnCode = process.exitValue();
        if (returnCode != 0) {
            throw new IllegalStateException("Failed to execute " + String.join(" ", commandLine) + " command return code " + returnCode);
        }

        return results;
    }
}
