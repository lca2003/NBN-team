package com.nbn.backend;

import com.nbn.backend.domain.ai.AiApiService;
import com.nbn.backend.domain.ads.AdApiService;
import com.nbn.backend.domain.ads.InteractionCommand;
import com.nbn.backend.domain.commerce.CommerceApiService;
import com.nbn.backend.domain.messages.MessageApiService;
import com.nbn.backend.domain.platform.PlatformApiService;
import com.nbn.backend.domain.reviews.ReviewCommentApiService;
import com.nbn.backend.domain.stitch.StitchPayloadService;
import com.nbn.backend.domain.users.UserApiService;
import com.nbn.backend.domain.users.UserSession;
import com.nbn.backend.http.ErrorCode;
import com.nbn.backend.http.JsonResponse;
import com.nbn.backend.store.JsonSeedStore;
import com.nbn.backend.store.SeedStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.json.JSONException;

public final class BackendServer implements AutoCloseable {
    public static final String SERVICE_NAME = "nbn-backend";
    public static final String VERSION = "0.1.0";

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_USER_ID_HEADER = "X-NBN-User-Id";
    private static final int DEFAULT_PORT = 8080;

    private final HttpServer server;
    private final Instant startedAt;
    private final JsonSeedStore seedStore;
    private final AiApiService aiApiService;
    private final AdApiService adApiService;
    private final CommerceApiService commerceApiService;
    private final MessageApiService messageApiService;
    private final PlatformApiService platformApiService;
    private final ReviewCommentApiService reviewCommentApiService;
    private final StitchPayloadService stitchPayloadService;
    private final UserApiService userApiService;
    private final UserSession userSession;

    private BackendServer(HttpServer server, Instant startedAt, JsonSeedStore seedStore) {
        this.server = server;
        this.startedAt = startedAt;
        this.seedStore = seedStore;
        this.userSession = new UserSession(seedStore.documentCopy("profile.json")
                .optString("currentUserId", UserApiService.CURRENT_USER_ID));
        this.userApiService = new UserApiService(seedStore, userSession);
        this.aiApiService = new AiApiService(seedStore);
        this.adApiService = new AdApiService(seedStore, userSession, userApiService);
        this.commerceApiService = new CommerceApiService(seedStore);
        this.messageApiService = new MessageApiService(seedStore, userSession);
        this.platformApiService = new PlatformApiService(seedStore);
        this.reviewCommentApiService = new ReviewCommentApiService(seedStore, adApiService, userSession);
        this.stitchPayloadService = new StitchPayloadService(seedStore, userSession, userApiService);
    }

    public static BackendServer create(int port) throws IOException {
        return create(port, null);
    }

    public static BackendServer create(int port, Path stateDirectory) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        BackendServer backendServer = new BackendServer(
                httpServer,
                Instant.now(),
                JsonSeedStore.loadDefault(BackendServer.class.getClassLoader(), stateDirectory)
        );
        httpServer.createContext("/", backendServer::handle);
        httpServer.setExecutor(Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "nbn-backend-http");
            thread.setDaemon(true);
            return thread;
        }));
        return backendServer;
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + port());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public SeedStatus seedStatus() {
        return seedStore.seedStatus();
    }

    public JsonSeedStore seedStore() {
        return seedStore;
    }

    public static void main(String[] args) throws IOException {
        BackendServer backendServer = BackendServer.create(configuredPort(args), configuredStateDirectory());
        Runtime.getRuntime().addShutdownHook(new Thread(backendServer::stop, "nbn-backend-shutdown"));
        backendServer.start();
        System.out.println(SERVICE_NAME + " listening on " + backendServer.baseUri());
        awaitShutdownSignal();
    }

    private static void awaitShutdownSignal() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static int configuredPort(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return parsePort(args[0], "first command argument");
        }
        String propertyPort = System.getProperty("nbn.backend.port");
        if (propertyPort != null && !propertyPort.isBlank()) {
            return parsePort(propertyPort, "nbn.backend.port");
        }
        String envPort = System.getenv("NBN_BACKEND_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return parsePort(envPort, "NBN_BACKEND_PORT");
        }
        return DEFAULT_PORT;
    }

    private static Path configuredStateDirectory() {
        String propertyStateDirectory = System.getProperty("nbn.backend.stateDir");
        if (propertyStateDirectory != null && !propertyStateDirectory.isBlank()) {
            return Path.of(propertyStateDirectory);
        }
        String envStateDirectory = System.getenv("NBN_BACKEND_STATE_DIR");
        if (envStateDirectory != null && !envStateDirectory.isBlank()) {
            return Path.of(envStateDirectory);
        }
        return Path.of("build", "nbn-backend-state");
    }

    private static int parsePort(String rawPort, String source) {
        try {
            int port = Integer.parseInt(rawPort.trim());
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException(source + " must be between 0 and 65535");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(source + " must be a number", exception);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String requestId = requestId(exchange);
        userSession.beginRequest(requestUserId(exchange));
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/health".equals(path) && "GET".equals(method)) {
                send(exchange, 200, requestId, JsonResponse.ok(requestId, healthJson()));
                return;
            }
            if ("/health".equals(path)) {
                send(exchange, 405, requestId, JsonResponse.error(
                        requestId,
                        ErrorCode.METHOD_NOT_ALLOWED,
                        "method not allowed"
                ));
                return;
            }
            if (routeV1(exchange, requestId, method, path)) {
                return;
            }
            send(exchange, 404, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.NOT_FOUND,
                    "resource not found"
            ));
        } catch (JSONException exception) {
            send(exchange, 400, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.BAD_REQUEST,
                    "bad request"
            ));
        } catch (RuntimeException exception) {
            send(exchange, 500, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.INTERNAL_ERROR,
                    "internal server error"
            ));
        } finally {
            userSession.clearRequest();
            exchange.close();
        }
    }

    private boolean routeV1(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        if ("/v1/feed/channels".equals(path)) {
            if (!"GET".equals(method)) {
                sendMethodNotAllowed(exchange, requestId);
                return true;
            }
            send(exchange, 200, requestId, JsonResponse.ok(requestId, adApiService.channelsJson()));
            return true;
        }
        if ("/v1/feed".equals(path)) {
            if (!"GET".equals(method)) {
                sendMethodNotAllowed(exchange, requestId);
                return true;
            }
            send(exchange, 200, requestId, JsonResponse.ok(
                    requestId,
                    adApiService.feedJson(exchange.getRequestURI())
            ));
            return true;
        }
        if (path.startsWith("/v1/ads/")) {
            routeAd(exchange, requestId, method, path);
            return true;
        }
        if (path.startsWith("/v1/merchants/") || "/v1/orders/checkout-intent".equals(path)) {
            routeCommerce(exchange, requestId, method, path);
            return true;
        }
        if (path.startsWith("/v1/reviews/") || path.equals("/v1/comments") || path.startsWith("/v1/comments/")) {
            routeReviewComment(exchange, requestId, method, path);
            return true;
        }
        if (path.startsWith("/v1/notifications")
                || path.startsWith("/v1/conversations")
                || "/v1/ai-assistant/digest".equals(path)) {
            routeMessage(exchange, requestId, method, path);
            return true;
        }
        if (path.startsWith("/v1/config/")
                || path.startsWith("/v1/assets/")
                || path.startsWith("/v1/design-content/")
                || path.startsWith("/v1/events/")
                || path.startsWith("/v1/exposures/")
                || path.startsWith("/v1/analytics/")) {
            routePlatform(exchange, requestId, method, path);
            return true;
        }
        if (path.startsWith("/v1/ai/")) {
            routeAi(exchange, requestId, method, path);
            return true;
        }
        if (path.startsWith("/v1/stitch/pages/")) {
            routeStitchPage(exchange, requestId, method, path);
            return true;
        }
        if (path.equals("/v1/auth/session") || path.equals("/v1/auth/register")
                || path.equals("/v1/auth/login") || path.equals("/v1/auth/logout")) {
            routeAuth(exchange, requestId, method, path);
            return true;
        }
        if (path.equals("/v1/users") || path.startsWith("/v1/users/")) {
            routeUser(exchange, requestId, method, path);
            return true;
        }
        return false;
    }

    private void routeAuth(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        try {
            String dataJson = null;
            if ("/v1/auth/session".equals(path) && "GET".equals(method)) {
                dataJson = userApiService.sessionJson();
            } else if ("/v1/auth/register".equals(path) && "POST".equals(method)) {
                dataJson = userApiService.register(readRequestBody(exchange));
            } else if ("/v1/auth/login".equals(path) && "POST".equals(method)) {
                dataJson = userApiService.login(readRequestBody(exchange));
            } else if ("/v1/auth/logout".equals(path) && "POST".equals(method)) {
                dataJson = userApiService.logout();
            }
            if (dataJson == null) {
                sendMethodNotAllowed(exchange, requestId);
                return;
            }
            send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.BAD_REQUEST,
                    exception.getMessage()
            ));
        }
    }

    private void routeCommerce(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        String dataJson = null;
        if ("/v1/orders/checkout-intent".equals(path)) {
            if (!"POST".equals(method)) {
                sendMethodNotAllowed(exchange, requestId);
                return;
            }
            dataJson = commerceApiService.checkoutIntentJson(readRequestBody(exchange));
        } else if (path.startsWith("/v1/merchants/")) {
            String remainder = path.substring("/v1/merchants/".length());
            String[] parts = remainder.split("/");
            String merchantId = decode(parts[0]);
            if (parts.length == 1 && "GET".equals(method)) {
                dataJson = commerceApiService.merchantJson(merchantId);
            } else if (parts.length == 2 && "nearby".equals(parts[1]) && "GET".equals(method)) {
                dataJson = commerceApiService.nearbyMerchantsJson(merchantId);
            } else if (parts.length == 1 || parts.length == 2 && "nearby".equals(parts[1])) {
                sendMethodNotAllowed(exchange, requestId);
                return;
            }
        }
        if (dataJson == null) {
            sendNotFound(exchange, requestId);
            return;
        }
        send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
    }

    private void routeReviewComment(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        try {
            String dataJson = null;
            if (path.startsWith("/v1/reviews/")) {
                String[] parts = path.substring("/v1/reviews/".length()).split("/");
                if (parts.length == 2 && "like".equals(parts[1]) && "POST".equals(method)) {
                    dataJson = reviewCommentApiService.likeReview(decode(parts[0]));
                } else if (parts.length == 2 && "like".equals(parts[1]) && "DELETE".equals(method)) {
                    dataJson = reviewCommentApiService.unlikeReview(decode(parts[0]));
                } else if (parts.length == 2 && "like".equals(parts[1])) {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
            } else if ("/v1/comments".equals(path)) {
                if ("GET".equals(method)) {
                    dataJson = reviewCommentApiService.commentsJson(exchange.getRequestURI());
                } else if ("POST".equals(method)) {
                    dataJson = reviewCommentApiService.createComment(readRequestBody(exchange));
                } else {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
            } else if (path.startsWith("/v1/comments/")) {
                String commentId = decode(path.substring("/v1/comments/".length()));
                if ("DELETE".equals(method)) {
                    dataJson = reviewCommentApiService.deleteComment(commentId);
                } else {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
            }
            if (dataJson == null) {
                sendNotFound(exchange, requestId);
                return;
            }
            send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.BAD_REQUEST,
                    exception.getMessage()
            ));
        }
    }

    private void routeMessage(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        try {
            String dataJson = null;
            if ("/v1/notifications/summary".equals(path)) {
                dataJson = "GET".equals(method) ? messageApiService.notificationSummaryJson() : null;
            } else if ("/v1/notifications".equals(path)) {
                dataJson = "GET".equals(method) ? messageApiService.notificationsJson(exchange.getRequestURI()) : null;
            } else if ("/v1/notifications/read".equals(path)) {
                dataJson = "POST".equals(method) ? messageApiService.markRead(readRequestBody(exchange)) : null;
            } else if ("/v1/notifications/read-all".equals(path)) {
                dataJson = "POST".equals(method) ? messageApiService.markAllRead() : null;
            } else if ("/v1/conversations".equals(path)) {
                dataJson = "GET".equals(method) ? messageApiService.conversationsJson(exchange.getRequestURI()) : null;
            } else if (path.startsWith("/v1/conversations/")) {
                String remainder = path.substring("/v1/conversations/".length());
                String[] parts = remainder.split("/");
                if (parts.length == 2 && "messages".equals(parts[1])) {
                    String conversationId = decode(parts[0]);
                    if ("GET".equals(method)) {
                        dataJson = messageApiService.messagesJson(conversationId, exchange.getRequestURI());
                    } else if ("POST".equals(method)) {
                        dataJson = messageApiService.appendMessage(conversationId, readRequestBody(exchange));
                    }
                }
            } else if ("/v1/ai-assistant/digest".equals(path)) {
                dataJson = "GET".equals(method) ? messageApiService.aiAssistantDigestJson() : null;
            }
            if (dataJson == null) {
                sendNotFound(exchange, requestId);
                return;
            }
            send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.BAD_REQUEST,
                    exception.getMessage()
            ));
        }
    }

    private void routePlatform(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        String dataJson = null;
        if ("/v1/config/app".equals(path)) {
            dataJson = "GET".equals(method) ? platformApiService.appConfigJson() : null;
        } else if ("/v1/assets/manifest".equals(path)) {
            dataJson = "GET".equals(method) ? platformApiService.assetManifestJson() : null;
        } else if ("/v1/design-content/home".equals(path)) {
            dataJson = "GET".equals(method) ? platformApiService.designContentHomeJson() : null;
        } else if ("/v1/events/batch".equals(path)) {
            dataJson = "POST".equals(method) ? platformApiService.recordEvents(readRequestBody(exchange)) : null;
        } else if ("/v1/exposures/batch".equals(path)) {
            dataJson = "POST".equals(method) ? platformApiService.recordExposures(readRequestBody(exchange)) : null;
        } else if ("/v1/analytics/summary".equals(path)) {
            dataJson = "GET".equals(method) ? platformApiService.analyticsSummaryJson() : null;
        }
        if (dataJson == null) {
            sendNotFound(exchange, requestId);
            return;
        }
        send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
    }

    private void routeAi(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        String dataJson = aiDataJson(exchange, method, path);
        if (dataJson == null) {
            sendNotFound(exchange, requestId);
            return;
        }
        send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
    }

    private String aiDataJson(HttpExchange exchange, String method, String path) throws IOException {
        if ("/v1/ai/search/suggestions".equals(path)) {
            return "GET".equals(method) ? aiApiService.suggestionsJson() : null;
        }
        if ("/v1/ai/search".equals(path)) {
            return "POST".equals(method) ? aiApiService.searchJson(readRequestBody(exchange)) : null;
        }
        if (path.startsWith("/v1/ai/search/sessions/")) {
            String remainder = path.substring("/v1/ai/search/sessions/".length());
            String[] parts = remainder.split("/");
            String sessionId = decode(parts[0]);
            if (parts.length == 1 && "GET".equals(method)) {
                return aiApiService.sessionJson(sessionId);
            }
            if (parts.length == 2 && "messages".equals(parts[1]) && "POST".equals(method)) {
                return aiApiService.appendMessageJson(sessionId, readRequestBody(exchange));
            }
            return null;
        }
        if ("/v1/ai/ads/rerank".equals(path)) {
            return "POST".equals(method) ? aiApiService.rerankJson(readRequestBody(exchange)) : null;
        }
        if (path.startsWith("/v1/ai/ads/")) {
            String remainder = path.substring("/v1/ai/ads/".length());
            String[] parts = remainder.split("/");
            if (parts.length != 2 || !"POST".equals(method)) {
                return null;
            }
            String adId = decode(parts[0]);
            if ("summary".equals(parts[1])) {
                return aiApiService.summaryJson(adId);
            }
            if ("tags".equals(parts[1])) {
                return aiApiService.tagsJson(adId);
            }
        }
        return null;
    }

    private void routeUser(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        try {
            String dataJson = userDataJson(exchange, method, path);
            if (dataJson == null) {
                sendNotFound(exchange, requestId);
                return;
            }
            send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.BAD_REQUEST,
                    exception.getMessage()
            ));
        }
    }

    private String userDataJson(HttpExchange exchange, String method, String path) throws IOException {
        if ("/v1/users".equals(path)) {
            return "POST".equals(method) ? userApiService.createUser(readRequestBody(exchange)) : null;
        }
        String remainder = path.substring("/v1/users/".length());
        String[] parts = remainder.split("/");
        String userId = decode(parts[0]);
        if (parts.length == 1) {
            if ("GET".equals(method)) {
                return "me".equals(userId) ? userApiService.meJson() : userApiService.userJson(userId);
            }
            if ("PATCH".equals(method) && "me".equals(userId)) {
                return userApiService.patchMe(readRequestBody(exchange));
            }
            return null;
        }
        String resource = parts[1];
        if ("stats".equals(resource) && "GET".equals(method) && "me".equals(userId)) {
            return userApiService.statsJson();
        }
        if ("achievements".equals(resource) && "GET".equals(method) && "me".equals(userId)) {
            return userApiService.achievementsJson();
        }
        if ("posts".equals(resource)) {
            if (parts.length == 2 && "GET".equals(method)) {
                return userApiService.postsJson(userId, queryParam(exchange.getRequestURI(), "tab"));
            }
            if (parts.length == 2 && "POST".equals(method) && "me".equals(userId)) {
                return userApiService.createPost(readRequestBody(exchange));
            }
            if (parts.length == 3 && "PATCH".equals(method) && "me".equals(userId)) {
                return userApiService.patchPost(decode(parts[2]), readRequestBody(exchange));
            }
            if (parts.length == 3 && "DELETE".equals(method) && "me".equals(userId)) {
                return userApiService.deletePost(decode(parts[2]));
            }
            return null;
        }
        if ("followers".equals(resource) && "GET".equals(method)) {
            return userApiService.followersJson(userId);
        }
        if ("following".equals(resource) && "GET".equals(method)) {
            return userApiService.followingJson(userId);
        }
        if ("follow".equals(resource) && "POST".equals(method)) {
            return userApiService.follow(userId);
        }
        if ("follow".equals(resource) && "DELETE".equals(method)) {
            return userApiService.unfollow(userId);
        }
        return null;
    }

    private void routeStitchPage(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        if (!"GET".equals(method)) {
            sendMethodNotAllowed(exchange, requestId);
            return;
        }
        String pageName = path.substring("/v1/stitch/pages/".length());
        String dataJson = stitchPayloadService.pagePayloadJson(pageName);
        if (dataJson == null) {
            sendNotFound(exchange, requestId);
            return;
        }
        send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
    }

    private void routeAd(
            HttpExchange exchange,
            String requestId,
            String method,
            String path
    ) throws IOException {
        try {
            String[] parts = path.substring("/v1/ads/".length()).split("/");
            if (parts.length == 0 || parts[0].isBlank()) {
                sendNotFound(exchange, requestId);
                return;
            }
            String adId = decode(parts[0]);
            String action = parts.length > 1 ? parts[1] : "";
            String dataJson;
            if (action.isBlank()) {
                if (!"GET".equals(method)) {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
                dataJson = adApiService.adJson(adId);
            } else if ("detail".equals(action)) {
                if (!"GET".equals(method)) {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
                dataJson = adApiService.detailJson(adId);
            } else if ("related".equals(action)) {
                if (!"GET".equals(method)) {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
                dataJson = adApiService.relatedJson(adId);
            } else if ("commerce".equals(action)) {
                if (!"GET".equals(method)) {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
                dataJson = commerceApiService.commerceJson(adId);
            } else if ("reviews".equals(action)) {
                if ("GET".equals(method)) {
                    dataJson = reviewCommentApiService.reviewsJson(adId, exchange.getRequestURI());
                } else if ("POST".equals(method)) {
                    dataJson = reviewCommentApiService.createReview(adId, readRequestBody(exchange));
                } else {
                    sendMethodNotAllowed(exchange, requestId);
                    return;
                }
            } else {
                dataJson = routeInteraction(exchange, requestId, method, adId, action);
                if (dataJson == null) {
                    return;
                }
            }
            if (dataJson == null) {
                sendNotFound(exchange, requestId);
                return;
            }
            send(exchange, 200, requestId, JsonResponse.ok(requestId, dataJson));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, requestId, JsonResponse.error(
                    requestId,
                    ErrorCode.BAD_REQUEST,
                    exception.getMessage()
            ));
        }
    }

    private String routeInteraction(
            HttpExchange exchange,
            String requestId,
            String method,
            String adId,
            String action
    ) throws IOException {
        InteractionCommand command;
        if ("exposure".equals(action) && "POST".equals(method)) {
            command = InteractionCommand.EXPOSURE;
        } else if ("like".equals(action) && "POST".equals(method)) {
            command = InteractionCommand.LIKE;
        } else if ("like".equals(action) && "DELETE".equals(method)) {
            command = InteractionCommand.UNLIKE;
        } else if ("collect".equals(action) && "POST".equals(method)) {
            command = InteractionCommand.COLLECT;
        } else if ("collect".equals(action) && "DELETE".equals(method)) {
            command = InteractionCommand.UNCOLLECT;
        } else if ("share".equals(action) && "POST".equals(method)) {
            command = InteractionCommand.SHARE;
        } else if ("click".equals(action) && "POST".equals(method)) {
            command = InteractionCommand.CLICK;
        } else if (
                "exposure".equals(action)
                        || "like".equals(action)
                        || "collect".equals(action)
                        || "share".equals(action)
                        || "click".equals(action)
        ) {
            sendMethodNotAllowed(exchange, requestId);
            return null;
        } else {
            sendNotFound(exchange, requestId);
            return null;
        }
        String dataJson = adApiService.applyInteraction(adId, command);
        if (dataJson == null) {
            sendNotFound(exchange, requestId);
            return null;
        }
        return dataJson;
    }

    private static void sendNotFound(HttpExchange exchange, String requestId) throws IOException {
        send(exchange, 404, requestId, JsonResponse.error(
                requestId,
                ErrorCode.NOT_FOUND,
                "resource not found"
        ));
    }

    private static void sendMethodNotAllowed(HttpExchange exchange, String requestId) throws IOException {
        send(exchange, 405, requestId, JsonResponse.error(
                requestId,
                ErrorCode.METHOD_NOT_ALLOWED,
                "method not allowed"
        ));
    }

    private String healthJson() {
        long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
        return "{"
                + "\"service\":\"" + JsonResponse.escape(SERVICE_NAME) + "\","
                + "\"version\":\"" + JsonResponse.escape(VERSION) + "\","
                + "\"startedAt\":\"" + JsonResponse.escape(startedAt.toString()) + "\","
                + "\"uptimeSeconds\":" + uptimeSeconds + ","
                + "\"seed\":" + seedStore.seedStatus().toJson() + ","
                + "\"state\":" + stateJson() + ","
                + "\"domains\":" + seedStore.domainSummaryJson()
                + "}";
    }

    private String stateJson() {
        return "{"
                + "\"persistenceEnabled\":" + seedStore.persistenceEnabled() + ","
                + "\"directory\":\"" + JsonResponse.escape(seedStore.stateDirectoryPath()) + "\""
                + "}";
    }

    private static String requestId(HttpExchange exchange) {
        List<String> requestIds = exchange.getRequestHeaders().get(REQUEST_ID_HEADER);
        if (requestIds != null && !requestIds.isEmpty() && !requestIds.get(0).isBlank()) {
            return requestIds.get(0);
        }
        return "req-" + UUID.randomUUID();
    }

    private static String requestUserId(HttpExchange exchange) {
        String userId = exchange.getRequestHeaders().getFirst(REQUEST_USER_ID_HEADER);
        return userId == null ? "" : userId.trim();
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String queryParam(URI uri, String key) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
            if (key.equals(decode(rawKey))) {
                return separator >= 0 ? decode(pair.substring(separator + 1)) : "";
            }
        }
        return "";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void send(
            HttpExchange exchange,
            int statusCode,
            String requestId,
            String body
    ) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.sendResponseHeaders(statusCode, payload.length);
        System.out.println(requestId + " "
                + exchange.getRequestMethod() + " "
                + exchange.getRequestURI().getPath() + " "
                + statusCode);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
