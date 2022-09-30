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

    static final Pattern range = Pattern.compile("bytes=(\\d*?)-(\\d*)");

    ODS(OdsServer server) {
        this.server = server;
    }

    /**
     * A get request is sent for reading a "chunk" of a disk using a supplied byte
     * range.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!req.getHeader("user-agent").equals("CCURLBS::readDataFork")) {
            logging.fine("User-Agent: " + req.getHeader("user-agent"));
            resp.sendError(403);
            return;
        }

        String _disk = req.getParameter("disk");
        String extension = tools.getExt(_disk);
        String basename = _disk.replace("." + extension, "");

        OnlineDisk disk = null;
        try {
            disk = this.server.disks().get(basename);
        } catch (IOException e) {
            logging.log(Level.SEVERE, "basename: " + basename, e);
            resp.sendError(404, "Disk not found");
            return;
        }

        int start = 0, end = 0;
        Matcher range = ODS.range.matcher(req.getHeader("range"));
        if (range.find()) {
            start = Integer.parseInt(range.group(1));
            end = Integer.parseInt(range.group(2));
        } else {
            logging.severe("range: " + range);
            resp.sendError(500, "Range decode error: " + range);
            return;
        }

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
        if (!req.getHeader("user-agent").equals("CCURLBS::statImage")) {
            logging.fine("User-Agent: " + req.getHeader("user-agent"));
            resp.sendError(403);
            return;
        }

        String _disk = req.getParameter("disk");
        String extension = tools.getExt(_disk);
        String basename = _disk.replace("." + extension, "");

        OnlineDisk disk = null;
        try {
            disk = this.server.disks().get(basename);
        } catch (IOException e) {
            logging.log(Level.SEVERE, "basename: " + basename, e);
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
