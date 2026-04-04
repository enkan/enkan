package kotowari.example.controller;

import enkan.data.ConversationState;
import enkan.web.data.HttpResponse;
import kotowari.component.TemplateEngine;

import jakarta.enterprise.context.Conversation;
import jakarta.inject.Inject;
import java.util.Random;

import static enkan.util.BeanBuilder.builder;

/**
 * @author kawasima
 */
public class ConversationStateController {
    @Inject
    private TemplateEngine<?> templateEngine;

    public HttpResponse page1(Conversation conversation) {
        if (conversation.isTransient()) conversation.begin();
        int randomValue = new Random().nextInt();
        ConversationState conversationState = new ConversationState();
        conversationState.put("random", randomValue);
        return builder(templateEngine.render("conversationState/page1",
                "random", randomValue))
                .set(HttpResponse::setConversationState, conversationState)
                .build();
    }

    public HttpResponse page2(ConversationState conversationState) {
        return templateEngine.render("conversationState/page2");
    }

    public HttpResponse page3(Conversation conversation) {
        if (!conversation.isTransient()) conversation.end();
        return templateEngine.render("conversationState/page3");
    }

}
