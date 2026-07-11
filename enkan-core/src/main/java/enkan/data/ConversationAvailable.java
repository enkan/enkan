package enkan.data;

import org.jspecify.annotations.Nullable;

import jakarta.enterprise.context.Conversation;

/**
 * Conversation
 *
 * @author kawasima
 */
public interface ConversationAvailable extends Extendable {
    default @Nullable Conversation getConversation() {
        return getExtension("conversation");
    }

    default void setConversation(Conversation conversation) {
        setExtension("conversation", conversation);
    }

    default @Nullable ConversationState getConversationState() {
        return getExtension("conversationState");
    }

    default void setConversationState(ConversationState conversationState) {
        setExtension("conversationState", conversationState);
    }

}
