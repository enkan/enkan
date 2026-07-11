package kotowari.inject.parameter;

import enkan.web.data.HttpRequest;
import kotowari.inject.ParameterInjector;

import org.jspecify.annotations.Nullable;

import jakarta.enterprise.context.Conversation;

public class ConversationInjector implements ParameterInjector<Conversation> {
    @Override
    public String getName() {
        return "Conversation";
    }

    @Override
    public boolean isApplicable(Class<?> type) {
        return Conversation.class.isAssignableFrom(type);
    }

    @Override
    public @Nullable Conversation getInjectObject(HttpRequest request) {
        return request.getConversation();
    }
}
