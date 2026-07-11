package enkan.system;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static enkan.system.ReplResponse.ResponseStatus.*;

/**
 * @author kawasima
 */
public class ReplResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private @Nullable String id;
    private final EnumSet<ResponseStatus> status;
    private @Nullable String value;
    private @Nullable String out;
    private @Nullable String err;

    public ReplResponse() {
        status = EnumSet.noneOf(ResponseStatus.class);
    }

    private ReplResponse(UUID id) {
        this();
        this.id = id.toString();
    }

    public static ReplResponse forRestore(UUID id) {
        return new ReplResponse(id);
    }

    public static ReplResponse withOut(String message) {
        ReplResponse response = new ReplResponse();
        response.setOut(message);
        return response;
    }

    public static ReplResponse withErr(String message) {
        ReplResponse response = new ReplResponse();
        response.setErr(message);
        response.status.add(ERROR);
        return response;
    }


    public enum ResponseStatus {
        SHUTDOWN, UNKNOWN_COMMAND, ERROR, DONE, NEED_INPUT
    }

    public @Nullable String getId() {
        return id;
    }

    public Set<ResponseStatus> getStatus() {
        return status;
    }

    public @Nullable String getValue() {
        return value;
    }

    public void setValue(@Nullable String value) {
        this.value = value;
    }

    public @Nullable String getOut() {
        return out;
    }

    public void setOut(@Nullable String out) {
        this.out = out;
    }

    public @Nullable String getErr() {
        return err;
    }

    public void setErr(@Nullable String err) {
        this.err = err;
    }

    public ReplResponse done() {
        this.status.add(DONE);
        return this;
    }
}
