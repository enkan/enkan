package enkan.web.middleware.multipart;

import enkan.collection.Multimap;

import java.io.File;
import java.util.List;

/**
 * Represents a multipart request.
 *
 * @author kawasima
 */
public record MultipartInfo(Multimap<String, String> params, List<File> tmpFiles) {
}
