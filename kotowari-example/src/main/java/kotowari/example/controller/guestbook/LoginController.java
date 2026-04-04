package kotowari.example.controller.guestbook;

import enkan.collection.Parameters;
import enkan.component.doma2.DomaProvider;
import enkan.web.data.HttpResponse;
import enkan.data.Session;
import enkan.web.util.HttpResponseUtils;
import kotowari.component.TemplateEngine;
import kotowari.example.dao.CustomerDao;
import kotowari.example.entity.Customer;
import kotowari.example.model.LoginPrincipal;
import kotowari.example.security.PasswordEncoder;

import jakarta.enterprise.context.Conversation;
import jakarta.inject.Inject;

import static enkan.util.BeanBuilder.builder;
import static kotowari.routing.UrlRewriter.redirect;

/**
 * @author kawasima
 */
public class LoginController {
    @Inject
    private DomaProvider daoProvider;

    @Inject
    private TemplateEngine<?> templateEngine;

    public HttpResponse loginForm(Parameters params, Conversation conversation) {
        if (conversation.isTransient()) conversation.begin();
        return templateEngine.render("guestbook/login",
                "url", params.get("url"));
    }

    public HttpResponse login(Parameters params, Conversation conversation) {
        if (!conversation.isTransient()) conversation.end();
        CustomerDao dao = daoProvider.getDao(CustomerDao.class);
        String email = params.get("email");
        Customer customer = dao.selectByEmail(email);
        if (customer == null || !PasswordEncoder.matches(params.get("password"), customer.getPassword())) {
            return templateEngine.render("guestbook/login");
        } else {
            Session session = new Session();
            session.put("principal", new LoginPrincipal(email));
            return builder(redirect(GuestbookController.class, "list", HttpResponseUtils.RedirectStatusCode.SEE_OTHER))
                    .set(HttpResponse::setSession, session)
                    .build();
        }
    }
}
