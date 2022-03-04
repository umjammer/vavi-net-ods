/*
 * https://github.com/klattimer/pyods
 */

package vavi.net.ods;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class OnlineDisk {

    static final Logger logging = Logger.getLogger(OnlineDisk.class.getName());

    Tools tools = Tools.getInstance();

    public enum OnlineDiskState {
        READY,
        NOT_READY,
        OPEN,
        EMPTY
    }

    protected Path _path;
    protected int _size = 0;
    protected String _label = null;
    protected OnlineDiskState _state = OnlineDiskState.EMPTY;

    OnlineDisk(Path path) {
        _path = path;
    }

    // property
    abstract boolean exists();

    // property
    public OnlineDiskState state() {
        return _state;
    }

    // property
    public Path path() {
        return _path;
    }

    // property
    public int size() throws IOException {
        return _size;
    }

    // property
    public String label() throws IOException {
        return _label;
    }

    abstract void erase();

    abstract void eject();

    public abstract byte[] read(int start, int end) throws IOException;

    @Override
    public int hashCode() {
        try {
            return String.format("%s:%d", label(), size()).hashCode();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    Map<String, Object> serialise() throws IOException {
        return new HashMap<String, Object>() {{
            put("label", label());
            put("size", size());
            put("filename", path().toString());
            put("state", state().toString());
        }};
    }

    /**
     * Concrete class for disc image file.
     */
    public static class DiskImage extends OnlineDisk {
        public DiskImage(Path path) {
            super(path);
            if (exists()) {
                _state = OnlineDiskState.READY;
            }
        }

        @Override
        boolean exists() {
            return Files.exists(path());
        }

        @Override
        public int size() throws IOException {
            if (_size == 0 && exists()) {
                _size = (int) Files.size(path());
            }
            return _size;
        }

        @Override
        public String label() throws IOException {
            if (_label == null) {
                _label = tools.getLabel(_path);
            }

            return _label;
        }

        @Override
        void erase() {
            // os.unlink ...
        }

        @Override
        public byte[] read(int start, int end) throws IOException {
            if (end == 0) {
                end = size() - 1;
            }

            if (end > size() - 1) {
                throw new IllegalArgumentException(String.format("end address exceeds size by %d bytes ", end - (size() - 1)));
            }

            if (start > end) {
                throw new IllegalArgumentException(String.format("start address exceeds end address %d > %d", start, end));
            }

            int len = end - start + 1;
            byte[] data = new byte[len];
            byte[] all = Files.readAllBytes(path());
            System.arraycopy(all, start, data, 0, len);
            return data;
        }

        @Override
        void eject() {
        }
    }

    /**
     * Concrete class for real optical disc.
     */
    public static class OpticalDrive extends OnlineDisk {
        protected int _block_size = 0;
        protected int _vol_size = 0;

        public OpticalDrive(Path path) {
            super(path);
        }

        // property
        public OnlineDiskState state() {
            OnlineDiskState old_state = _state;
            OnlineDiskState _state;
            try {
                _state = tools.state(_path);
            } catch (IOException e) {
                logging.log(Level.SEVERE, e.getMessage(), e);
                return OnlineDiskState.NOT_READY;
            }

            if (old_state != _state) {
                // Change handler...

                _size = 0;
                _label = null;
            }
            return _state;
        }

        @Override
        public String label() throws IOException {
            if (_label == null) {
                _label = tools.getLabel(_path);
            }

            return _label;
        }

        int[] block_size() {
            return new int[] { _block_size, _vol_size };
        }

        @Override
        public int size() throws IOException {
            if (_size == 0) {
                int[] sizes = tools.block_size(_path);
                _block_size = sizes[0];
                _vol_size = sizes[1];
            }
            _size = _vol_size * _block_size;

            return _size;
        }

        @Override
        void erase() {
            // erase disk
            // check if re-writable media is in first
        }

        @Override
        void eject() {
        }

        @Override
        public byte[] read(int start, int end) throws IOException {
            if (end == 0) {
                end = size() - 1;
            }

            if (end > size() - 1) {
                throw new IllegalArgumentException(String.format("end address exceeds size by %d bytes ", end - (size() - 1)));
            }

            if (start > end) {
                throw new IllegalArgumentException(String.format("start address exceeds end address %d > %d", start, end));
            }

            int len = end - start + 1;
            byte[] data = new byte[len];
            byte[] all = Files.readAllBytes(path());
            System.arraycopy(all, start, data, 0, len);
            return data;
        }

        @Override
        boolean exists() {
            return false;
        }
    }

    /**
     * Concrete class for removal disc.
     */
    public static class RemovableDrive extends OnlineDisk {
        RemovableDrive(Path path) {
            super(path);
        }

        @Override
        public String label() {
            if (_label == null) {
                // Get the label of the removable drive or, the label of the first partition
            }
            return _label;
        }

        @Override
        boolean exists() {
            return Files.exists(path());
        }

        @Override
        void erase() {
            // erase disk
        }

        @Override
        void eject() {
        }

        @Override
        public byte[] read(int start, int end) throws IOException {
            if (end == 0) {
                end = size() - 1;
            }

            if (end > size() - 1) {
                throw new IllegalArgumentException(String.format("end address exceeds size by %d bytes ", end - (size() - 1)));
            }

            if (start > end) {
                throw new IllegalArgumentException(String.format("start address exceeds end address %d > %d", start, end));
            }

            int len = end - start + 1;
            byte[] data = new byte[len];
            byte[] all = Files.readAllBytes(path());
            System.arraycopy(all, start, data, 0, len);
            return data;
        }
    }
}
