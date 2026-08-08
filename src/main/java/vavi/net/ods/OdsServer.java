/*
 * https://github.com/klattimer/pyods
 */

package vavi.net.ods;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;

import vavi.net.ods.OnlineDisk.DiskImage;
import vavi.net.ods.OnlineDisk.OpticalDrive;


public class OdsServer {
    static final Logger logging = Logger.getLogger(OdsServer.class.getName());

    static Preferences config = Preferences.userNodeForPackage(OdsServer.class);

    static {
        try {
            String mountPoint = System.getProperty("vavi.net.ods.OdsServer.mountPoint");
            if (mountPoint != null) {
                config.put("root", mountPoint);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Server server;

    Tools tools = Tools.getInstance();

    /** overrides the configured images directory, null to use the configured one */
    private final String root;

    /** overrides the configured port, 0 to use the configured one */
    private final int port;

    public interface Plugin {
        void update() throws IOException;
    }

    List<OnlineDisk> drives;
    List<OnlineDisk> images;
    Plugin plugin;

    // property
    public Map<String, OnlineDisk> disks() throws IOException {
        if (drives == null || images == null) {
            update();
        }
        List<OnlineDisk> all = new ArrayList<>();
        all.addAll(drives);
        all.addAll(images);
        Map<String, OnlineDisk> result = new HashMap<>();
        AtomicInteger count = new AtomicInteger(0);
        all.forEach(i -> {
           result.put(String.format("disk%d", count.getAndIncrement()), i);
        });
        return result;
    }

    // property
    public String host() {
        return config.get("host", tools.getLocalIp().getHostAddress());
    }

    // property
    public int port() {
        return port != 0 ? port : config.getInt("port", 49152);
    }

    public String root() {
        return root != null ? root : config.get("root", "/mnt/images");
    }

    public void update() throws IOException {
        boolean changed;
        int h;
        if (images == null || drives == null) {
            changed = true;
            h = 0;
        } else {
            changed = false;
            h = images.hashCode();
        }

        images = tools.listImages(root()).stream().map(DiskImage::new).collect(Collectors.toList());

        if (images.hashCode() != h) {
            changed = true;
        }

        try {
            if (changed == false) {
                h = drives.hashCode();
            }
            drives = tools.listOpticalDrives().stream().map(OpticalDrive::new).collect(Collectors.toList());
            if (changed == false && drives.hashCode() != h) {
                changed = true;
            }
        } catch (Exception e) {
            drives = Collections.emptyList();
        }

        if (changed) {
            if (plugin != null) {
                plugin.update();
            }
        }
    }

    OdsServer() throws IOException {
        this(null, 0);
    }

    /**
     * @param root the directory the disc images are served from, null for the
     *             configured one
     * @param port the port to serve them on, 0 for the configured one
     */
    OdsServer(String root, int port) throws IOException {
        this.root = root;
        this.port = port;

        if (!Files.exists(Paths.get(root()))) {
            // Create the images path if (possible, otherwise raise an error
            // but continue gracefully.
        }

        images = null;
        drives = null;

        plugin = new Bonjour(this);
    }

    /** Starts serving and waits for the server to be shut down. */
    void run() throws IOException {
        start();

        try {
            server.join();
        } catch (Exception e) { // let me down
            throw new IOException(e);
        }
    }

    /** Starts serving and returns as soon as the server is up. */
    void start() throws IOException {
        logging.info("Starting webserver");

        server = new Server();

        try {
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port());
            server.setConnectors(new Connector[]{connector});

            ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/");
            // register instances: both servlets need a reference to this server,
            // so they have no no-arg constructor for jetty to reflect on
            ServletHolder ods = new ServletHolder(new ODS(this));
            context.addServlet(ods, "/");
            // hdiutil can only attach a plain path, so serve /disk0.iso as well
            for (String ext : new String[] {"*.iso", "*.img", "*.dmg"}) {
                context.addServlet(ods, ext);
            }
            context.addServlet(new ServletHolder(new Image(this)), "/images");

            // jetty 12 spells a HandlerCollection tried in order as a Sequence
            server.setHandler(new Handler.Sequence(context, new DefaultHandler()));

            server.start();
        } catch (Exception e) { // let me down
            throw new IOException(e);
        }
    }

    void stop() throws IOException {
        try {
            server.stop();
        } catch (Exception e) { // let me down
            throw new IOException(e);
        } finally {
            // or the discs stay announced to everyone browsing, pointing at nothing
            if (plugin instanceof Closeable) {
                ((Closeable) plugin).close();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        logging.info("Starting vavi-nio-ods remote disk server");
        OdsServer s = new OdsServer();
        s.run();
    }
}