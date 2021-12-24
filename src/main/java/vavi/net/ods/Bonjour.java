/*
 * https://github.com/klattimer/pyods
 */

package vavi.net.ods;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceInfo;

import vavi.net.ods.OdsServer.Plugin;


public class Bonjour implements Plugin {
    static final Logger logging = Logger.getLogger(Bonjour.class.getName());

    OdsServer server;
    JmmDNS zeroconf;
    ServiceInfo info = null;

    public Bonjour(OdsServer server) throws IOException {
        this.server = server;
        zeroconf = JmmDNS.Factory.getInstance();
        update();
    }

    public void update() throws IOException {
        remove();
        String hostname = InetAddress.getLocalHost().getHostName();

        Map<String, String> desc = new HashMap<>();
        desc.put("sys", "waMA=A4:BA:DB:E7:89:CD,adVF=0x4,adDT=0x3,adCC=1");

        for (Map.Entry<String, OnlineDisk> e : server.disks().entrySet()) {
            String ident = e.getKey();
            OnlineDisk disk = e.getValue();
            desc.put(ident, String.format("adVN=%s,adVT=public.cd-media", disk.label()));
            logging.info(String.format("Announcing disk \"%s\" as %s with name \"%s\"", disk.path(), ident, disk.label()));
        }

        info = ServiceInfo.create(
            "_odisk._tcp.local.",
            String.format("%s._odisk._tcp.local.", hostname),
            server.host(),
            server.port(),
            0, 0,
            desc
        );

        add();
    }

    void add() throws IOException {
        if (info == null) {
            return;
        }
        zeroconf.registerService(info);
    }

    void remove() {
        if (info == null) {
            return;
        }
        zeroconf.unregisterService(info);
    }

    protected void finalize() throws IOException {
        zeroconf.unregisterAllServices();
        zeroconf.close();
    }
}