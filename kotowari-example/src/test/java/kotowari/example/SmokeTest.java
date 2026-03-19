package kotowari.example;

import com.microsoft.playwright.*;
import enkan.system.EnkanSystem;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke tests for kotowari-example using Playwright.
 *
 * <p>Starts the full Enkan system (Undertow + H2 in-memory) in {@code @BeforeAll},
 * runs browser-based tests, and stops everything in {@code @AfterAll}.</p>
 *
 * <p>Screenshots are saved to {@code target/screenshots/} for each test.</p>
 */
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmokeTest {
    private static final Path SCREENSHOT_DIR = Paths.get("target", "screenshots");

    private String baseUrl;

    private EnkanSystem system;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    @BeforeAll
    void startSystem() throws Exception {
        SCREENSHOT_DIR.toFile().mkdirs();

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
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 720));
    }

    @AfterAll
    void stopSystem() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        if (system != null) system.stop();
    }

    private void screenshot(Page page, String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(SCREENSHOT_DIR.resolve(name + ".png"))
                .setFullPage(true));
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
        screenshot(page, "01-index");
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
        screenshot(page, "02-customer-list");
        page.close();
    }

    @Test
    @Order(3)
    void customerNewFormAndCreate() {
        Page page = context.newPage();
        page.navigate(baseUrl + "/customer/new");
        assertThat(page.locator("form").count()).isPositive();
        screenshot(page, "03a-customer-new-form");

        page.fill("input[name='name']", "Charlie");
        page.fill("input[name='email']", "charlie@example.com");
        page.fill("input[name='password']", "secret123");
        page.click("input#gender-M");
        page.evaluate("document.querySelector('input[name=birthday]').value = '2000-03-15'");
        screenshot(page, "03b-customer-new-filled");

        page.click("button[type='submit']");

        // create redirects to customer index via SEE_OTHER
        page.waitForLoadState();
        assertThat(page.content()).contains("Charlie");
        screenshot(page, "03c-customer-after-create");
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
        screenshot(page, "10-guestbook-login-redirect");
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
        screenshot(page, "11a-guestbook-login-filled");
        page.click("button[type='submit']");

        // After login, should be on guestbook list
        page.waitForURL("**/guestbook/**");
        assertThat(page.url()).contains("/guestbook/");
        screenshot(page, "11b-guestbook-list-after-login");

        // Post a message
        page.fill("input[name='message']", "Hello from Playwright!");
        page.click("button[type='submit']");

        // After posting, should see the message
        page.waitForURL("**/guestbook/**");
        assertThat(page.content()).contains("Hello from Playwright!");
        screenshot(page, "11c-guestbook-after-post");
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
        screenshot(page, "20a-counter-first");

        // Second visit should increment
        page.navigate(baseUrl + "/misc/counter");
        assertThat(page.content()).contains("2times");
        screenshot(page, "20b-counter-second");
        page.close();
    }

    // -----------------------------------------------------------------------
    // Static resources
    // -----------------------------------------------------------------------

    @Test
    @Order(30)
    void staticCssIsServed() {
        Page page = context.newPage();
        Response response = page.navigate(baseUrl + "/");
        assertThat(response.status()).isEqualTo(200);
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
        screenshot(page, "50-not-found");
        page.close();
    }
}
