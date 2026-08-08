/*
 * https://github.com/klattimer/pyods
 */

package vavi.net.ods;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import vavi.net.ods.OdsServer.Plugin;
import vavi.util.Debug;


public class Bonjour implements Plugin {
    static final Logger logging = Logger.getLogger(Bonjour.class.getName());

    /** the bonjour service type macOS's Finder browses for a remote disc */
    static final String TYPE = "_odisk._tcp.local.";

    OdsServer server;
    JmDNS zeroconf;
    ServiceInfo info = null;

    public Bonjour(OdsServer server) throws IOException {
        this.server = server;
        // bind to the address we advertise, not to loopback, or nothing on the lan sees us
        InetAddress address = InetAddress.getByName(server.host());
        zeroconf = JmDNS.create(address, InetAddress.getLocalHost().getHostName());
Debug.println("zeroconf: " + address);
        update();
    }

    public void update() throws IOException {
        remove();
        String hostname = InetAddress.getLocalHost().getHostName().replaceFirst("\\.local\\.?$", "");
Debug.println("hostname: " + hostname);

        Map<String, String> desc = new HashMap<>();
        desc.put("sys", "waMA=A4:BA:DB:E7:89:CD,adVF=0x4,adDT=0x3,adCC=1");

        for (Map.Entry<String, OnlineDisk> e : server.disks().entrySet()) {
            String ident = e.getKey();
            OnlineDisk disk = e.getValue();
            desc.put(ident, String.format("adVN=%s,adVT=public.cd-media", disk.label()));
            logging.info(String.format("Announcing disk \"%s\" as %s with name \"%s\"", disk.path(), ident, disk.label()));
        }

        info = ServiceInfo.create(
            TYPE,
            hostname,
            server.port(),
            0, 0,
            desc
        );
Debug.println("host: " + server.host() + ":" + server.port());
Debug.println("info: " + info.getQualifiedName());

        add();
    }

    void add() throws IOException {
        if (info == null) {
            return;
        }
        zeroconf.registerService(info);
Debug.println("added");
    }

    void remove() {
        if (info == null) {
            return;
        }
        zeroconf.unregisterService(info);
        info = null;
Debug.println("removed");
    }

    protected void finalize() throws IOException {
        zeroconf.unregisterAllServices();
        zeroconf.close();
    }
}
