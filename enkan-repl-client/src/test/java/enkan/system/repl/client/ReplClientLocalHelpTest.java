package enkan.system.repl.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplClientLocalHelpTest {
    @Test
    void localCommandNamesContainClientCommands() {
        assertThat(ReplClient.localCommandNames())
                .contains("/connect", "/help", "/init", "/exit");
    }

    @Test
    void formatLocalHelpShowsAllClientCommands() {
        String help = ReplClient.formatLocalHelp("/help");

        assertThat(help)
                .contains("Client commands")
                .contains("/connect")
                .contains("/help")
                .contains("/init")
                .contains("/exit");
    }

    @Test
    void formatLocalHelpShowsSingleCommandDetail() {
        String help = ReplClient.formatLocalHelp("/help connect");

        assertThat(help).contains("/connect [host] port");
    }

    @Test
    void formatLocalHelpShowsUnknownForUnsupportedCommand() {
        String help = ReplClient.formatLocalHelp("/help unknown");

        assertThat(help).isEqualTo("Unknown client command: unknown");
    }

    // ------------------------------------------------------------------------
    //  isValidHost: hostname/IP validation
    // ------------------------------------------------------------------------

    @Test
    void isValidHostAcceptsLocalhostAndIpAddresses() {
        assertThat(ReplClient.isValidHost("localhost")).isTrue();
        assertThat(ReplClient.isValidHost("127.0.0.1")).isTrue();
        assertThat(ReplClient.isValidHost("my-host.example.com")).isTrue();
        assertThat(ReplClient.isValidHost("enkan-server")).isTrue();
    }

    @Test
    void isValidHostRejectsSpecialCharacters() {
        assertThat(ReplClient.isValidHost("localhost;rm -rf ~")).isFalse();
        assertThat(ReplClient.isValidHost("@inproc://monitor-")).isFalse();
        assertThat(ReplClient.isValidHost("host name")).isFalse();
        assertThat(ReplClient.isValidHost("")).isFalse();
        assertThat(ReplClient.isValidHost(null)).isFalse();
    }
}
