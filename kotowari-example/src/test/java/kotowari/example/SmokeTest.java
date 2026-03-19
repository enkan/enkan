package kotowari.example;

import com.microsoft.playwright.*;
import enkan.system.EnkanSystem;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke tests for kotowari-example using Playwright.
 *
 * <p>Starts the full Enkan system (Undertow + H2 in-memory) in {@code @BeforeAll},
 * runs browser-based tests, and stops everything in {@code @AfterAll}.</p>
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmokeTest {
    private String baseUrl;

    private EnkanSystem system;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    @BeforeAll
    void startSystem() throws Exception {
        int port;
        try (var ss = new java.net.ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        baseUrl = "http://localhost:" + port;

        System.setProperty("enkan.env", "production");
        System.setProperty("PORT", Integer.toString(port));
        system = new ExampleSystemFactory().create();
        system.start();

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        context = browser.newContext();
    }

    @AfterAll
    void stopSystem() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        if (system != null) system.stop();
    }

    // -----------------------------------------------------------------------
    // Index page
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    void indexPageLoadsAndShowsExampleLinks() {
        Page page = context.newPage();
        Response response = page.navigate(baseUrl + "/");

        assertThat(response.status()).isEqualTo(200);
        assertThat(page.title()).contains("Example");
        assertThat(page.locator("h1").textContent()).contains("Kotowari examples");
        assertThat(page.locator("a:has-text('Counter')").count()).isPositive();
        assertThat(page.locator("a:has-text('CRUD')").count()).isPositive();
        assertThat(page.locator("a:has-text('Guestbook')").count()).isPositive();
        page.close();
    }

    // -----------------------------------------------------------------------
    // Customer CRUD
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    void customerListPageLoads() {
        Page page = context.newPage();
        Response response = page.navigate(baseUrl + "/customer");

        assertThat(response.status()).isEqualTo(200);
        assertThat(page.content()).contains("Alice");
        assertThat(page.content()).contains("Bob");
        page.close();
    }

    @Test
    @Order(3)
    void customerNewFormAndCreate() {
        Page page = context.newPage();
        page.navigate(baseUrl + "/customer/new");
        assertThat(page.locator("form").count()).isPositive();

        page.fill("input[name='name']", "Charlie");
        page.fill("input[name='email']", "charlie@example.com");
        page.fill("input[name='password']", "secret123");
        page.click("input#gender-M");
        // Use evaluate to set date input value (fill may not work on type="date" in headless)
        page.evaluate("document.querySelector('input[name=birthday]').value = '2000-03-15'");
        page.click("button[type='submit']");

        // create redirects to customer index via SEE_OTHER
        page.waitForLoadState();
        assertThat(page.content()).contains("Charlie");
        page.close();
    }

    // -----------------------------------------------------------------------
    // Guestbook (login required)
    // -----------------------------------------------------------------------

    @Test
    @Order(10)
    void guestbookRedirectsToLoginWhenUnauthenticated() {
        Page page = context.newPage();
        page.navigate(baseUrl + "/guestbook/");

        assertThat(page.url()).contains("/guestbook/login");
        assertThat(page.locator("input[name='email']").count()).isPositive();
        assertThat(page.locator("input[name='password']").count()).isPositive();
        page.close();
    }

    @Test
    @Order(11)
    void guestbookLoginAndPostMessage() {
        Page page = context.newPage();
        page.navigate(baseUrl + "/guestbook/");

        // Login
        page.fill("input[name='email']", "alice@example.com");
        page.fill("input[name='password']", "password");
        page.click("button[type='submit']");

        // After login, should be on guestbook list
        page.waitForURL("**/guestbook/**");
        assertThat(page.url()).contains("/guestbook/");

        // Post a message
        page.fill("input[name='message']", "Hello from Playwright!");
        page.click("button[type='submit']");

        // After posting, should see the message
        page.waitForURL("**/guestbook/**");
        assertThat(page.content()).contains("Hello from Playwright!");
        page.close();
    }

    // -----------------------------------------------------------------------
    // Session counter
    // -----------------------------------------------------------------------

    @Test
    @Order(20)
    void sessionCounterIncrements() {
        Page page = context.newPage();
        Response response = page.navigate(baseUrl + "/misc/counter");

        assertThat(response.status()).isEqualTo(200);
        assertThat(page.content()).contains("1times");

        // Second visit should increment
        page.navigate(baseUrl + "/misc/counter");
        assertThat(page.content()).contains("2times");
        page.close();
    }

    // -----------------------------------------------------------------------
    // Static resources
    // -----------------------------------------------------------------------

    @Test
    @Order(30)
    void staticCssIsServed() {
        Page page = context.newPage();
        // Index page loads bootstrap CSS via link tag — just verify the page renders with styles
        Response response = page.navigate(baseUrl + "/");
        assertThat(response.status()).isEqualTo(200);
        // Page should have at least one stylesheet link
        assertThat(page.locator("link[rel='stylesheet']").count()).isPositive();
        page.close();
    }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    @Test
    @Order(50)
    void notFoundReturns404() {
        Page page = context.newPage();
        Response response = page.navigate(baseUrl + "/nonexistent");

        assertThat(response.status()).isEqualTo(404);
        page.close();
    }
}
