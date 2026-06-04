package com.nbn.adfeed.data.remote;

import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HttpApiClientTest {
    private SimpleHttpServer server;

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void sendsGetPostPatchAndDeleteRequestsOverRealHttp() throws Exception {
        server = new SimpleHttpServer(4, 200, "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":{}}");
        HttpApiClient client = new HttpApiClient(new BackendConfig(
                server.baseUrl(),
                1_000,
                1_000,
                0,
                true
        ));

        client.get("/v1/probe");
        client.post("/v1/probe", "{\"name\":\"post\"}");
        client.patch("/v1/probe", "{\"name\":\"patch\"}");
        client.delete("/v1/probe");

        assertEquals("GET", server.methods.get(0));
        assertEquals("POST", server.methods.get(1));
        assertEquals("PATCH", server.methods.get(2));
        assertEquals("DELETE", server.methods.get(3));
        assertTrue(server.bodies.get(1).contains("\"name\":\"post\""));
        assertTrue(server.bodies.get(2).contains("\"name\":\"patch\""));
    }

    @Test
    public void mapsHttpErrorsToRemoteException() throws Exception {
        server = new SimpleHttpServer(
                1,
                503,
                "{\"requestId\":\"req-test\",\"code\":\"REMOTE_ERROR\",\"message\":\"down\",\"data\":null}"
        );
        HttpApiClient client = new HttpApiClient(new BackendConfig(
                server.baseUrl(),
                1_000,
                1_000,
                0,
                true
        ));

        try {
            client.get("/v1/fail");
        } catch (RemoteAdException exception) {
            assertEquals(RemoteAdException.Reason.INVALID_RESPONSE, exception.getReason());
            assertTrue(exception.getMessage().contains("503"));
            return;
        }
        throw new AssertionError("Expected RemoteAdException");
    }

    private static final class SimpleHttpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final int maxRequests;
        private final int statusCode;
        private final String body;
        private final Thread thread;
        private final List<String> methods = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();

        private SimpleHttpServer(int maxRequests, int statusCode, String body) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.maxRequests = maxRequests;
            this.statusCode = statusCode;
            this.body = body;
            this.thread = new Thread(this::serve, "http-api-client-test-server");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        private void serve() {
            for (int request = 0; request < maxRequests && !serverSocket.isClosed(); request++) {
                try (Socket socket = serverSocket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                    ));
                    String requestLine = reader.readLine();
                    if (requestLine != null && !requestLine.isBlank()) {
                        methods.add(requestLine.split(" ")[0]);
                    }
                    String line;
                    int contentLength = 0;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                        }
                        // Drain headers before sending the response.
                    }
                    char[] bodyChars = new char[contentLength];
                    int offset = 0;
                    while (offset < contentLength) {
                        int read = reader.read(bodyChars, offset, contentLength - offset);
                        if (read < 0) {
                            break;
                        }
                        offset += read;
                    }
                    bodies.add(new String(bodyChars, 0, offset));
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    String response = "HTTP/1.1 " + statusCode + " OK\r\n"
                            + "Content-Type: application/json; charset=utf-8\r\n"
                            + "Content-Length: " + payload.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(response.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(payload);
                    outputStream.flush();
                } catch (IOException ignored) {
                    return;
                }
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(1_000);
        }
    }
}
