package enkan.web.data;

public interface HasStatus {
    /**
     * Returns a HTTP status code.
     *
     * @return the current status code of this response
     */
    int getStatus();

    /**
     * Sets a HTTP status code.
     *
     * @param status a status code.
     */
    void setStatus(int status);
}
