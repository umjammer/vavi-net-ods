/*
 * https://github.com/klattimer/pyods
 */

package vavi.net.ods;

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

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;

import vavi.net.ods.OnlineDisk.DiskImage;
import vavi.net.ods.OnlineDisk.OpticalDrive;


public class OdsServer {
    static final Logger logging = Logger.getLogger(OdsServer.class.getName());

    Server server;

    Tools tools = Tools.getInstance();

    public interface Plugin {
        void update() throws IOException;
    }

    Preferences config;
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
        AtomicInteger count = new AtomicInteger(1);
        all.forEach(i -> {
           result.put(String.format("disk%d", count.incrementAndGet()), i);
        });
        return result;
    }

    // property
    public String host() {
        return config.get("host", tools.get_local_ip().toString());
    }

    // property
    public int port() {
        return config.getInt("port", 65432);
    }

    public String root() {
        return config.get("root", "/mnt/images");
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

        images = tools.list_images(root()).stream().map(DiskImage::new).collect(Collectors.toList());

        if (images.hashCode() != h) {
            changed = true;
        }

        try {
            if (changed == false) {
                h = drives.hashCode();
            }
            drives = tools.list_optical_drives().stream().map(OpticalDrive::new).collect(Collectors.toList());
            if (changed == false && drives.hashCode() != h) {
                changed = true;
            }
        } catch (Exception e) {
            drives = Collections.emptyList();
        }

        if (changed) {
            plugin.update();
        }
    }

    OdsServer() throws IOException {

        if (!Files.exists(Paths.get(root()))) {
            // Create the images path if (possible, otherwise raise an error
            // but continue gracefully.
        }

        images = null;
        drives = null;

        plugin = new Bonjour(this);
    }

    void run() throws IOException {
        logging.info("Starting webserver");

        server = new Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port());
        server.setConnectors(new Connector[]{connector});

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(ODS.class, "/");
        context.addServlet(Image.class, "/images");

        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[]{context, new DefaultHandler()});
        server.setHandler(handlers);

        try {
            server.start();
            server.join();
        } catch (Exception e) { // let me down
            throw new IOException(e);
        }
    }

    void stop() throws IOException {
        try {
            server.stop();
        } catch (Exception e) { // let me down
            throw new IOException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        logging.info("Starting pyods remote disk server");
        OdsServer s = new OdsServer();
        s.run();
    }
}