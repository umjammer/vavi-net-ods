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
        return new LinuxTools();
    }

    public InetAddress get_local_ip() {
        try {
            Socket s = new Socket();
            // s.socket.AF_INET, socket.SOCK_DGRAM);
            s.connect(InetSocketAddress.createUnresolved("10.255.255.255", 1));
            s.close();
            return s.getLocalAddress();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getExt(String filename) {
        if (filename.lastIndexOf('.') == -1) {
            return null;
        }

        return filename.substring(0, filename.lastIndexOf('.'));
    }

    public boolean is_image(Path path) {
        String ext = getExt(path.getFileName().toString());
        if (ext == null) {
            return false;
        } else {
            return Arrays.asList("iso", "img", "dmg").contains(ext);
        }
    }

    public List<Path> list_images(String path) throws IOException {
        return Files.list(Paths.get(path)).filter(this::is_image).collect(Collectors.toList());
    }

    protected String _getdev(String line) {
        final Pattern p = Pattern.compile("dev='(.*?)'");
        Matcher m = p.matcher(line);
        return m.find(1) ? m.group(0) : null;
    }

    public abstract List<Path> list_optical_drives() throws IOException;

    public abstract String getLabel(Path path) throws IOException;

    public abstract int[] block_size(Path path) throws IOException;

    public abstract OnlineDiskState state(Path path) throws IOException;

    static class LinuxTools extends Tools {
        public String list_removable_drives() {
            String script = "grep -Hv ^0$ /sys/block/*/removable |" +
                    "sed s/removable:.*$/device\\/uevent/ |" +
                    "xargs grep -H ^DRIVER=sd |" +
                    "sed s/device.uevent.*$/size/ |" +
                    "xargs grep -Hv ^0$ |" +
                    "cut -d / -f 4";
            return script;
        }

        public List<Path> list_optical_drives() throws IOException {

            List<Path> drives = new ArrayList<>();

            for (String l : exec("wodim", "--devices")) {
                String d = _getdev(l);
                if (d != null && !d.isEmpty()) {
                    drives.add(Paths.get(d));
                }
            }

            Collections.sort(drives);
            return drives;
        }

        public String getLabel(Path path) throws IOException {
            List<String> out = Tools.exec("isoinfo", "-d", "-i", path.toString());

            for (String line : out) {
                if (line.startsWith("volume id:") && line.length() > 11) {
                    return line.substring(11);
                }
            }

            return null;
        }

        public int[] block_size(Path path) throws IOException {
            List<String> out = Tools.exec("isoinfo", "-d", "-i", path.toString());

            int block_size = 0, vol_size = 0;
            for (String line : out) {
                if (line.startsWith("volume size is:") && line.length() > 16) {
                    vol_size = Integer.valueOf(line.substring(16));
                }

                if (line.startsWith("logical block size is:") && line.length() > 23) {
                    block_size = Integer.valueOf(line.substring(23));
                }
            }
            return new int[] { block_size, vol_size };
        }

        @Override
        public OnlineDiskState state(Path path) throws IOException {
            final Pattern _type_re = Pattern.compile("type (\\d*?)");
            @SuppressWarnings("unused")
            int _type;
            OnlineDiskState _state = OnlineDiskState.NOT_READY;
            String output;
                output = Tools.exec("setcd", "-i", path.toString()).get(0);

            if (output.contains("is open")) {
                _state = OnlineDiskState.OPEN;
            }
            if (output.contains("not ready")) {
                _state = OnlineDiskState.NOT_READY;
            }

            if (output.contains("disc found")) {
                // "Disc found in drive: data disc type 1";
                Matcher m = _type_re.matcher(output);
                _type = Integer.valueOf(m.group(1));

                // TODO Extract type
                _state = OnlineDiskState.READY;
            }

            if (output.contains("no disc")) {
                _state = OnlineDiskState.EMPTY;
            }

            return _state;
        }
    }

    protected static List<String> exec(String... commandLine) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        Process process = processBuilder.start();
        Scanner s = new Scanner(process.getInputStream());
        List<String> results = new ArrayList<>();
        while (s.hasNextLine()) {
            results.add(s.nextLine());
        }
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        s.close();

        int returncode = process.exitValue();
        if (returncode != 0) {
            throw new IllegalStateException("Failed to execute wodim command return code " + returncode);
        }

        return results;
    }
}
