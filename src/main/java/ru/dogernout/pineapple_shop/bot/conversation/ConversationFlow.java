package ru.dogernout.pineapple_shop.bot.conversation;

/**
 * Один экземпляр flow = один конкретный диалог с конкретным chatId. Хранит свой шаг
 * и накопленные данные как обычные instance-поля — отдельный enum/Map состояния
 * снаружи не нужен, это и есть весь "стейт".
 * <p>
 * Жизненный цикл: ConversationManager.start() создаёт экземпляр flow и кладёт
 * его в себя, вызывая onEnter(). Каждое следующее текстовое сообщение от этого
 * chatId, пока flow активен, уходит в onTextInput(); нажатия inline-кнопок
 * подтверждения/отмены — в onCallback(). Flow сам решает, когда он завершён,
 * и сам вызывает conversationManager.finish(chatId).
 */
public interface ConversationFlow {

    void onEnter();

    void onTextInput(String text);

    default void onCallback(String callbackData) {
        throw new UnsupportedOperationException("Этот сценарий не ожидает нажатий кнопок на текущем шаге");
    }
}
