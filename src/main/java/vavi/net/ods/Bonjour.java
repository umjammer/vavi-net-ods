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
import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.JmmDNSImpl;
import javax.jmdns.impl.NetworkTopologyEventImpl;

import vavi.net.ods.OdsServer.Plugin;
import vavi.util.Debug;


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
            server.host() + "@" + hostname + "_odisk._tcp.local.",
            server.host() + "@" + hostname,
            server.port(),
            0, 0,
            desc
        );
Debug.println("host: " + server.host() + ":" + server.port());
Debug.println("info: " + info);

        ((JmmDNSImpl) zeroconf).inetAddressAdded(new NetworkTopologyEventImpl(
            JmDNS.create(InetAddress.getByName("localhost")), InetAddress.getByName("localhost")));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

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
Debug.println("removed");
    }

    protected void finalize() throws IOException {
        zeroconf.unregisterAllServices();
        zeroconf.close();
    }
}