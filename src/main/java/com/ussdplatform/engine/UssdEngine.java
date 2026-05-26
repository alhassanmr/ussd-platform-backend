package com.ussdplatform.engine;

import com.ussdplatform.gateway.UssdRequest;
import com.ussdplatform.gateway.UssdResponse;
import com.ussdplatform.model.*;
import com.ussdplatform.repository.MenuRepository;
import com.ussdplatform.repository.UssdSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UssdEngine {

    private final UssdSessionRepository sessionRepository;
    private final MenuRepository menuRepository;
    private final SessionCache sessionCache;
    private final WebClient.Builder webClientBuilder;

    @Transactional
    public UssdResponse process(UssdApp app, UssdRequest request) {
        log.debug("Processing USSD [{}] session={} input='{}'",
                app.getName(), request.getSessionId(), request.getInput());

        UssdSession session = loadOrCreateSession(app, request);

        if (request.isNew() || session.getCurrentMenu() == null) {
            Menu rootMenu = menuRepository.findRootMenuByAppId(app.getId())
                    .orElseThrow(() -> new RuntimeException("No root menu configured for app: " + app.getId()));
            session.setCurrentMenu(rootMenu);
            session.setCurrentItem(null);
            sessionRepository.save(session);
            return buildMenuResponse(rootMenu, session);
        }

        return handleInput(session, request.getInput());
    }

    private UssdResponse handleInput(UssdSession session, String input) {
        Menu currentMenu = session.getCurrentMenu();
        List<MenuItem> items = currentMenu.getItems();

        if (input == null || input.isBlank()) {
            return buildMenuResponse(currentMenu, session);
        }

        try {
            int choice = Integer.parseInt(input.trim());
            if (choice >= 1 && choice <= items.size()) {
                MenuItem selected = items.get(choice - 1);
                return processItem(selected, session, input);
            } else {
                return UssdResponse.builder()
                        .message("Invalid option. Please try again.\n\n" + buildMenuText(currentMenu, session))
                        .shouldContinue(true)
                        .build();
            }
        } catch (NumberFormatException e) {
            MenuItem currentItem = session.getCurrentItem();
            if (currentItem != null && currentItem.getItemType() == MenuItem.ItemType.INPUT) {
                return processInputItem(currentItem, session, input);
            }
            return UssdResponse.builder()
                    .message("Invalid input. Please try again.\n\n" + buildMenuText(currentMenu, session))
                    .shouldContinue(true)
                    .build();
        }
    }

    private UssdResponse processItem(MenuItem item, UssdSession session, String input) {
        return switch (item.getItemType()) {
            case DISPLAY -> {
                session.setCurrentItem(item);
                if (item.getNextMenu() != null) {
                    session.setCurrentMenu(item.getNextMenu());
                    sessionRepository.save(session);
                    yield buildMenuResponse(item.getNextMenu(), session);
                }
                sessionRepository.save(session);
                yield buildMenuResponse(session.getCurrentMenu(), session);
            }
            case INPUT -> {
                session.setCurrentItem(item);
                sessionRepository.save(session);
                yield UssdResponse.builder()
                        .message(item.getInputPrompt() != null ? item.getInputPrompt() : item.getLabel())
                        .shouldContinue(true)
                        .build();
            }
            case WEBHOOK -> processWebhook(item, session);
            case END -> {
                endSession(session);
                yield UssdResponse.builder()
                        .message(item.getEndMessage() != null ? item.getEndMessage() : "Thank you. Goodbye!")
                        .shouldContinue(false)
                        .build();
            }
            case ROUTER -> {
                if (item.getNextMenu() != null) {
                    session.setCurrentMenu(item.getNextMenu());
                    session.setCurrentItem(null);
                    sessionRepository.save(session);
                    yield buildMenuResponse(item.getNextMenu(), session);
                }
                yield buildMenuResponse(session.getCurrentMenu(), session);
            }
        };
    }

    private UssdResponse processInputItem(MenuItem item, UssdSession session, String input) {
        if (item.getVariableName() != null) {
            session.getSessionData().put(item.getVariableName(), input);
        }
        if (item.getNextMenu() != null) {
            session.setCurrentMenu(item.getNextMenu());
            session.setCurrentItem(null);
            sessionRepository.save(session);
            return buildMenuResponse(item.getNextMenu(), session);
        }
        sessionRepository.save(session);
        return buildMenuResponse(session.getCurrentMenu(), session);
    }

    private UssdResponse processWebhook(MenuItem item, UssdSession session) {
        try {
            WebClient client = webClientBuilder.build();
            String responseBody = client
                    .method("GET".equalsIgnoreCase(item.getWebhookMethod())
                            ? org.springframework.http.HttpMethod.GET
                            : org.springframework.http.HttpMethod.POST)
                    .uri(item.getWebhookUrl())
                    .bodyValue(session.getSessionData())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (item.getNextMenu() != null) {
                session.setCurrentMenu(item.getNextMenu());
                session.setCurrentItem(null);
                sessionRepository.save(session);
                return buildMenuResponse(item.getNextMenu(), session);
            }

            return UssdResponse.builder()
                    .message(responseBody != null ? responseBody : "Done.")
                    .shouldContinue(item.getNextMenu() != null)
                    .build();
        } catch (Exception e) {
            log.error("Webhook call failed for item {}", item.getId(), e);
            return UssdResponse.builder()
                    .message("Service temporarily unavailable. Please try again.")
                    .shouldContinue(false)
                    .build();
        }
    }

    private UssdResponse buildMenuResponse(Menu menu, UssdSession session) {
        return UssdResponse.builder()
                .message(buildMenuText(menu, session))
                .shouldContinue(true)
                .build();
    }

    private String buildMenuText(Menu menu, UssdSession session) {
        StringBuilder sb = new StringBuilder();
        List<MenuItem> items = menu.getItems();
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            sb.append(i + 1).append(". ")
              .append(interpolateVariables(item.getLabel(), session.getSessionData()));
            if (i < items.size() - 1) sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String interpolateVariables(String text, Map<String, String> data) {
        if (text == null || data == null) return text;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return text;
    }

    private UssdSession loadOrCreateSession(UssdApp app, UssdRequest request) {
        return sessionRepository.findBySessionId(request.getSessionId())
                .orElseGet(() -> {
                    UssdSession s = UssdSession.builder()
                            .sessionId(request.getSessionId())
                            .app(app)
                            .msisdn(request.getMsisdn())
                            .status(UssdSession.SessionStatus.ACTIVE)
                            .build();
                    return sessionRepository.save(s);
                });
    }

    private void endSession(UssdSession session) {
        session.setStatus(UssdSession.SessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }
}
