package ru.dogernout.pineapple_shop.bot.conversation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * При двух пользователях и единицах операций в неделю персистентность состояния
 * диалога избыточна — обычный ConcurrentHashMap в памяти процесса полностью
 * решает задачу. Если бот перезапустится посреди диалога, пользователь просто
 * начнёт сценарий заново — это приемлемая цена за простоту при таком масштабе.
 */
@Component
public class ConversationManager {

    private final Map<Long, ConversationFlow> activeFlows = new ConcurrentHashMap<>();

    public void start(Long chatId, ConversationFlow flow) {
        activeFlows.put(chatId, flow);
        flow.onEnter();
    }

    public boolean hasActive(Long chatId) {
        return activeFlows.containsKey(chatId);
    }

    public Optional<ConversationFlow> get(Long chatId) {
        return Optional.ofNullable(activeFlows.get(chatId));
    }

    public void finish(Long chatId) {
        activeFlows.remove(chatId);
    }
}
