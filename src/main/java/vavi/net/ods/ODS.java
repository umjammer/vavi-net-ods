/*
 * https://github.com/klattimer/pyods
 */

package vavi.net.ods;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vavi.net.ods.OnlineDisk.OnlineDiskState;


public class ODS extends HttpServlet {

    static final Logger logging = Logger.getLogger(ODS.class.getName());

    OdsServer server;

    Tools tools = Tools.getInstance();

    static final Pattern range = Pattern.compile("bytes=(\\d+)-(\\d*)");

    /** what apple's remote disc client calls itself when it asks for a disc's size */
    static final String STAT_USER_AGENT = "CCURLBS::statImage";

    /** what it calls itself when it reads disc content */
    static final String READ_USER_AGENT = "CCURLBS::readDataFork";

    ODS(OdsServer server) {
        this.server = server;
    }

    /**
     * Resolves the disk identifier from either {@code /?disk=disk0.iso} (how finder's
     * remote disc client asks) or {@code /disk0.iso} (how hdiutil asks: its
     * CCURLBackingStore refuses urls carrying a query string).
     */
    private String resolveDisk(HttpServletRequest req) {
        String _disk = req.getParameter("disk");
        if (_disk == null) {
            String uri = req.getRequestURI();
            _disk = uri.substring(uri.lastIndexOf('/') + 1);
        }
        if (_disk.isEmpty()) {
            return null;
        }
        String extension = tools.getExt(_disk);
        return extension == null ? _disk : _disk.substring(0, _disk.length() - extension.length() - 1);
    }

    /**
     * A get request is sent for reading a "chunk" of a disk using a supplied byte
     * range.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!READ_USER_AGENT.equals(req.getHeader("user-agent"))) {
            logging.fine("User-Agent: " + req.getHeader("user-agent"));
            resp.sendError(403);
            return;
        }

        String basename = resolveDisk(req);
        if (basename == null) {
            resp.sendError(404, "Disk not found");
            return;
        }

        OnlineDisk disk = null;
        try {
            disk = this.server.disks().get(basename);
        } catch (IOException e) {
            logging.log(Level.SEVERE, "basename: " + basename, e);
            resp.sendError(404, "Disk not found");
            return;
        }

        // nobody shares a disc by that name
        if (disk == null) {
            resp.sendError(404, "Disk not found");
            return;
        }

        String requested = req.getHeader("range");
        int start, end;
        Matcher range = requested == null ? null : ODS.range.matcher(requested);
        if (range != null && range.find()) {
            start = Integer.parseInt(range.group(1));
            // an open ended "bytes=n-" asks for everything that is left
            end = range.group(2).isEmpty() ? disk.size() - 1 : Integer.parseInt(range.group(2));
        } else {
            logging.fine("range: " + requested);
            resp.sendError(400, "Range decode error: " + requested);
            return;
        }

        if (start >= disk.size() || end < start) {
            resp.sendError(416, "Range out of bounds: " + requested);
            return;
        }
        end = Math.min(end, disk.size() - 1);

        byte[] data;
        if (disk.state() == OnlineDiskState.READY) {
            data = disk.read(start, end);
        } else {
            resp.sendError(404, "Device not ready");
            return;
        }

        resp.setHeader("Content-Type", "application/octet-stream");
        resp.setHeader("Server", "ODS/1.0");

        resp.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, disk.size()));
        DataOutput out = new DataOutputStream(resp.getOutputStream());
        out.write(data);
    }

    /**
     * Return the header indicating the disk size in bytes and the current date/time
     */
    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!STAT_USER_AGENT.equals(req.getHeader("user-agent"))) {
            logging.fine("User-Agent: " + req.getHeader("user-agent"));
            resp.sendError(403);
            return;
        }

        String basename = resolveDisk(req);
        if (basename == null) {
            resp.sendError(404, "Disk not found");
            return;
        }

        OnlineDisk disk = null;
        try {
            disk = this.server.disks().get(basename);
        } catch (IOException e) {
            logging.log(Level.SEVERE, "basename: " + basename, e);
            resp.sendError(404, "Disk not found");
            return;
        }

        // nobody shares a disc by that name
        if (disk == null) {
            resp.sendError(404, "Disk not found");
            return;
        }

        resp.setHeader("Content-Type", "application/octet-stream");
        resp.setHeader("Server", "ODS/1.0");

        resp.setHeader("Date", LocalDateTime.now().toString());
        resp.setHeader("Accept-Ranges", "bytes");
        resp.setHeader("Content-Length", String.valueOf(disk.size()));
    }
}
