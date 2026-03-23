package enkan.config;

import enkan.component.SystemComponent;
import enkan.exception.UnreachableException;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A reloadable classloader.
 *
 * @author kawasima
 */
public class ConfigurationLoader extends ClassLoader {
    private final List<File> dirs;
    private final Map<String, Boolean> systemComponentCache = new HashMap<>();

    public ConfigurationLoader(ClassLoader parent) {
        super(parent);
        URL[] urls = getURLs(parent);

        dirs = Arrays.stream(urls)
                .filter(ConfigurationLoader::isDirectoryUrl)
                .filter(ConfigurationLoader::hasReloadDescriptorUrl)
                .map(url -> {
                    try {
                        return new File(url.toURI());
                    } catch(URISyntaxException e) {
                        throw new UnreachableException(e);
                    }
                })
                .toList();
    }

    private static boolean hasReloadDescriptorUrl(URL dir) {
        try {
            return Files.exists(new File(dir.toURI()).toPath().resolve("META-INF/reload.xml"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private URL[] getURLs(ClassLoader parent) {
        if (parent instanceof java.net.URLClassLoader ucl) {
            return ucl.getURLs();
        }
        String cp = System.getProperty("java.class.path", "");
        String[] elements = cp.isEmpty() ? new String[]{""} : cp.split(File.pathSeparator);
        URL[] urls = new URL[elements.length];
        for (int i = 0; i < elements.length; i++) {
            try {
                urls[i] = new File(elements[i]).toURI().toURL();
            } catch (IllegalArgumentException ignore) {
                // malformed file string or class path element does not exist
            } catch (java.net.MalformedURLException ignore) {
                // should not happen for File URIs
            }
        }
        return urls;
    }

    protected boolean contains(File dir, String path) {
        return Files.exists(dir.toPath().resolve(path.replace('.', '/') + ".class"));
    }

    private static boolean isDirectoryUrl(URL url) {
        try {
            return url.getProtocol().equals("file") && new File(url.toURI()).isDirectory();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    protected boolean isDirectory(URL url) {
        return isDirectoryUrl(url);
    }

    protected boolean isTarget(String name) {
        return dirs.stream()
                .anyMatch(d -> contains(d, name));
    }

    private void definePackage(String name) {
        int idx = name.lastIndexOf('.');
        if (idx > 0) {
            String pkgName = name.substring(0, idx);
            Package pkg = getDefinedPackage(pkgName);
            if (pkg == null) {
                definePackage(pkgName, null, null, null, null, null, null, null);
            }
        }
    }

    /**
     * Checks whether the given class is a {@link SystemComponent} subclass
     * by walking the superclass chain via bytecode inspection. When a
     * superclass is also in a reload-target directory, its bytecode is
     * read directly instead of loading it into the parent classloader.
     */
    private boolean isSystemComponentSubclass(String name) {
        Boolean cached = systemComponentCache.get(name);
        if (cached != null) return cached;
        boolean result = checkSystemComponentSubclass(name);
        systemComponentCache.put(name, result);
        return result;
    }

    private boolean checkSystemComponentSubclass(String name) {
        String current = name;
        while (current != null && !"java.lang.Object".equals(current)) {
            if (SystemComponent.class.getName().equals(current)) {
                return true;
            }
            // If the superclass is also a reload target, read its bytecode
            // directly to avoid loading it into the parent classloader.
            if (isTarget(current) && !current.equals(name)) {
                current = readSuperClassName(current);
                continue;
            }
            // Non-target superclass: safe to load via parent
            if (!current.equals(name)) {
                try {
                    Class<?> superClass = getParent().loadClass(current);
                    return SystemComponent.class.isAssignableFrom(superClass);
                } catch (ClassNotFoundException e) {
                    return false;
                }
            }
            current = readSuperClassName(current);
        }
        return false;
    }

    /**
     * Extracts the superclass name from a class file's constant pool
     * without defining the class. Returns {@code null} if the bytecode
     * cannot be read.
     */
    private String readSuperClassName(String name) {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream raw = getResourceAsStream(resource);
             DataInputStream in = raw != null ? new DataInputStream(raw) : null) {
            if (in == null) return null;

            int magic = in.readInt();
            if (magic != 0xCAFEBABE) return null;
            in.readUnsignedShort(); // minor version
            in.readUnsignedShort(); // major version

            // Read constant pool
            int cpCount = in.readUnsignedShort();
            Object[] cp = new Object[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> cp[i] = in.readUTF(); // CONSTANT_Utf8
                    case 7 -> cp[i] = in.readUnsignedShort(); // CONSTANT_Class → name index
                    case 3, 4 -> in.readInt(); // CONSTANT_Integer, Float
                    case 5, 6 -> { in.readLong(); i++; } // CONSTANT_Long, Double (2 slots)
                    case 8 -> in.readUnsignedShort(); // CONSTANT_String
                    case 9, 10, 11, 12 -> { in.readUnsignedShort(); in.readUnsignedShort(); }
                    case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); } // MethodHandle
                    case 16 -> in.readUnsignedShort(); // MethodType
                    case 17, 18 -> { in.readUnsignedShort(); in.readUnsignedShort(); } // Dynamic, InvokeDynamic
                    case 19, 20 -> in.readUnsignedShort(); // Module, Package
                    default -> { return null; }
                }
            }

            in.readUnsignedShort(); // access flags
            in.readUnsignedShort(); // this_class
            int superClassIndex = in.readUnsignedShort();
            if (superClassIndex == 0) return null;

            // Resolve: CONSTANT_Class → name_index → Utf8
            if (cp[superClassIndex] instanceof Integer nameIndex
                    && cp[nameIndex] instanceof String internalName) {
                return internalName.replace('/', '.');
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private Class<?> defineClass(String name, boolean resolve) {
        try (InputStream in = getResourceAsStream(name.replaceAll("\\.", "/") + ".class");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (in == null) return null;
            int len;
            final byte[] buffer = new byte[1024];
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            byte[] classBinary = baos.toByteArray();

            Class<?> c = defineClass(name, classBinary, 0, classBinary.length);
            definePackage(name);
            if (resolve)
                resolveClass(c);
            return c;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (isTarget(name)) {
                Class<?> c = findLoadedClass(name);
                if (c != null) return c;

                // SystemComponent subclasses must not be redefined — their instances
                // are created by the parent classloader in EnkanSystem.of(), so
                // redefining them here would cause classloader mismatch on injection.
                // Use bytecode inspection to avoid loading the class into the parent,
                // which would prevent hot-reload of non-component classes.
                if (isSystemComponentSubclass(name)) {
                    return super.loadClass(name, resolve);
                }
                c = defineClass(name, resolve);
                if (c != null) return c;
            }
            return super.loadClass(name, resolve);
        }
    }

    public List<File> reloadableFiles() {
        return dirs;
    }
}
