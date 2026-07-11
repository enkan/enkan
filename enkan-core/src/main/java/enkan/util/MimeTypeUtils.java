package enkan.util;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author kawasima
 */
public class MimeTypeUtils {
    static final Map<String, String> DEFAULT_MIME_TYPES = new HashMap<>() {{
        put("7z", "application/x-7z-compressed");
        put("aac", "audio/aac");
        put("ai", "application/postscript");
        put("appcache", "text/cache-manifest");
        put("asc", "text/plain");
        put("atom", "application/atom+xml");
        put("avi", "video/x-msvideo");
        put("bin", "application/octet-stream");
        put("bmp", "image/bmp");
        put("bz2", "application/x-bzip");
        put("class", "application/octet-stream");
        put("cer", "application/pkix-cert");
        put("crl", "application/pkix-crl");
        put("crt", "application/x-x509-ca-cert");
        put("css", "text/css");
        put("csv", "text/csv");
        put("deb", "application/x-deb");
        put("dart", "application/dart");
        put("dll", "application/octet-stream");
        put("dmg", "application/octet-stream");
        put("dms", "application/octet-stream");
        put("doc", "application/msword");
        put("dvi", "application/x-dvi");
        put("edn", "application/edn");
        put("eot", "application/vnd.ms-fontobject");
        put("eps", "application/postscript");
        put("etx", "text/x-setext");
        put("exe", "application/octet-stream");
        put("flv", "video/x-flv");
        put("flac", "audio/flac");
        put("gif", "image/gif");
        put("gz", "application/gzip");
        put("htm", "text/html");
        put("html", "text/html");
        put("ico", "image/x-icon");
        put("iso", "application/x-iso9660-image");
        put("jar", "application/java-archive");
        put("jpe", "image/jpeg");
        put("jpeg", "image/jpeg");
        put("jpg", "image/jpeg");
        put("js", "text/javascript");
        put("json", "application/javascript");
        put("lha", "application/octet-stream");
        put("lzh", "application/octet-stream");
        put("mov", "video/quicktime");
        put("m4v", "video/mp4");
        put("mp3", "audio/mpeg");
        put("mp4", "video/mp4");
        put("mpe", "video/mpeg");
        put("mpeg", "video/mpeg");
        put("mpg", "video/mpeg");
        put("oga", "audio/ogg");
        put("ogg", "audio/ogg");
        put("ogv", "video/ogg");
        put("pbm", "image/x-portable-bitmap");
        put("pdf", "application/pdf");
        put("pgm", "image/x-portable-graymap");
        put("png", "image/png");
        put("pnm", "image/x-portable-anymap");
        put("ppm", "image/x-portable-pixmap");
        put("ppt", "application/vnd.ms-powerpoint");
        put("ps", "application/postscript");
        put("qt", "video/quicktime");
        put("rar", "application/x-rar-compressed");
        put("ras", "image/x-cmu-raster");
        put("rb", "text/plain");
        put("rd", "text/plain");
        put("rss", "application/rss+xml");
        put("rtf", "application/rtf");
        put("sgm", "text/sgml");
        put("sgml", "text/sgml");
        put("svg", "image/svg+xml");
        put("swf", "application/x-shockwave-flash");
        put("tar", "application/x-tar");
        put("tif", "image/tiff");
        put("tiff", "image/tiff");
        put("ttf", "application/x-font-ttf");
        put("txt", "text/plain");
        put("webm", "video/webm");
        put("wmv", "video/x-ms-wmv");
        put("woff", "application/font-woff");
        put("xbm", "image/x-xbitmap");
        put("xls", "application/vnd.ms-excel");
        put("xml", "text/xml");
        put("xpm", "image/x-xpixmap");
        put("xwd", "image/x-xwindowdump");
        put("zip", "application/zip");
    }};

    private static @Nullable String filenameExt(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx == -1 || idx == filename.length() - 1) {
            return null;
        } else {
            return filename.substring(idx + 1);
        }

    }

    public static @Nullable String extMimeType(String filename) {
        String ext = filenameExt(filename);
        return ext != null ? DEFAULT_MIME_TYPES.get(ext) : null;
    }
}
