/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.ods;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;


/**
 * Whether bonjour can work at all where the tests are running. A build machine is
 * often a vm whose network drops multicast - a ci runner among them - and there an
 * announcement nobody can see says nothing about the server that made it.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 */
final class Mdns {

    /** a type of our own, so a real service on the network cannot answer for it */
    static final String PROBE_TYPE = "_odsprobe._tcp.local.";

    static final long PROBE_TIMEOUT = 15_000;

    private static Boolean works;

    private Mdns() {
    }

    /**
     * Announces a service of our own and looks for it, which is the same round trip
     * the server and a browsing mac make.
     *
     * @param address the interface to announce on
     * @return whether an announcement made here can be seen here
     */
    static synchronized boolean works(InetAddress address) {
        if (works != null) {
            return works;
        }

        try (JmDNS announcer = JmDNS.create(address, "ods-probe-announcer");
             JmDNS browser = JmDNS.create(address, "ods-probe-browser")) {

            announcer.registerService(ServiceInfo.create(PROBE_TYPE, "probe", 1, "ods probe"));

            long deadline = System.currentTimeMillis() + PROBE_TIMEOUT;
            do {
                if (browser.list(PROBE_TYPE, 5_000).length > 0) {
                    works = true;
                    return works;
                }
            } while (System.currentTimeMillis() < deadline);

            works = false;
        } catch (IOException e) {
            works = false;
        }

        return works;
    }
}
