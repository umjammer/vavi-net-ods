/*
 * https://github.com/klattimer/pyods
 */

 package vavi.net.ods;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class Image extends HttpServlet {
    static final Logger logging = Logger.getLogger(Image.class.getName());

    OdsServer server;

    Tools tools = Tools.getInstance();

    Image(OdsServer server) {
        this.server = server;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String image = req.getParameter("image");
        // Upload image to the image bucket
        int size = 0;
        boolean success = false;
        try {
            Path path = Paths.get(server.root(), image.toString());
            if (!tools.is_image(path)) {
                resp.sendError(403, "Unsupported image file");
                return;
            }
            try (OutputStream out = resp.getOutputStream();
                 InputStream in = Files.newInputStream(path)) {
                while (true) {
                    byte[] data = new byte[8192];
                    int r = in.read(data, 0, data.length);

                    if (r == -1) {
                        break;
                    }

                    size += r;
                    out.write(data, 0, r);
                }
            }
            this.server.update();
            success = true;
        } catch (IOException e) {
            logging.log(Level.SEVERE, "Failed to store upload", e);
            resp.sendError(500);
            return;
        }

        resp.setHeader("size", String.valueOf(size));
        resp.setHeader("success", String.valueOf(success));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            PrintWriter writer = resp.getWriter();
            this.server.disks().keySet().forEach(writer::println); // TODO
        } catch (IOException e) {
            logging.log(Level.SEVERE, e.getMessage(), e);
            resp.sendError(500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // server.disks().get(image_name).erase();
    }
}
