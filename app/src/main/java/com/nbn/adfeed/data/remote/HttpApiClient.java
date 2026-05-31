package com.nbn.adfeed.data.remote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

public final class HttpApiClient implements BackendStitchDataSource.Transport,
        BackendRemoteAdDataSource.Transport,
        BackendUserDataSource.Transport,
        BackendMessageDataSource.Transport,
        BackendReviewDataSource.Transport,
        BackendPlatformDataSource.Transport {
    private final BackendConfig config;

    public HttpApiClient(BackendConfig config) {
        this.config = config == null ? BackendConfig.defaultConfig() : config;
    }

    @Override
    public String get(String path) throws RemoteAdException {
        return request("GET", path, "");
    }

    @Override
    public String post(String path) throws RemoteAdException {
        return request("POST", path, "");
    }

    @Override
    public String post(String path, String body) throws RemoteAdException {
        return request("POST", path, body);
    }

    @Override
    public String patch(String path, String body) throws RemoteAdException {
        return request("PATCH", path, body);
    }

    @Override
    public String delete(String path) throws RemoteAdException {
        return request("DELETE", path, "");
    }

    private String request(String method, String path, String body) throws RemoteAdException {
        RemoteAdException lastException = null;
        int attempts = config.getRetryCount() + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return execute(method, path, body);
            } catch (RemoteAdException exception) {
                lastException = exception;
            }
        }
        throw lastException == null
                ? new RemoteAdException(RemoteAdException.Reason.UNKNOWN, "Backend request failed")
                : lastException;
    }

    private String execute(String method, String path, String body) throws RemoteAdException {
        if ("PATCH".equals(method)) {
            return executeRawHttp(method, path, body);
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(config.getApiBaseUrl() + normalizePath(path));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(config.getConnectTimeoutMs());
            connection.setReadTimeout(config.getReadTimeoutMs());
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Request-Id", "app-" + UUID.randomUUID());
            if (body != null && !body.isEmpty()) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
            int statusCode = connection.getResponseCode();
            String responseBody = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            }
            throw new RemoteAdException(
                    RemoteAdException.Reason.INVALID_RESPONSE,
                    "Backend returned HTTP " + statusCode
            );
        } catch (java.net.SocketTimeoutException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.TIMEOUT, exception.getMessage());
        } catch (IOException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.NETWORK, exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String executeRawHttp(String method, String path, String body) throws RemoteAdException {
        Socket socket = null;
        try {
            URL url = new URL(config.getApiBaseUrl() + normalizePath(path));
            socket = openSocket(url);
            String target = url.getFile().isEmpty() ? "/" : url.getFile();
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder headers = new StringBuilder()
                    .append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
                    .append("Host: ").append(hostHeader(url)).append("\r\n")
                    .append("Accept: application/json\r\n")
                    .append("X-Request-Id: app-").append(UUID.randomUUID()).append("\r\n")
                    .append("Connection: close\r\n");
            if (bodyBytes.length > 0) {
                headers.append("Content-Type: application/json; charset=utf-8\r\n")
                        .append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            }
            headers.append("\r\n");

            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));
            if (bodyBytes.length > 0) {
                outputStream.write(bodyBytes);
            }
            outputStream.flush();

            InputStream inputStream = socket.getInputStream();
            String statusLine = readAsciiLine(inputStream);
            int statusCode = parseStatusCode(statusLine);
            int contentLength = -1;
            String headerLine;
            while ((headerLine = readAsciiLine(inputStream)) != null && !headerLine.isEmpty()) {
                String lowerCaseHeader = headerLine.toLowerCase(Locale.ROOT);
                if (lowerCaseHeader.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.substring(headerLine.indexOf(':') + 1).trim());
                }
            }
            String responseBody = readRawBody(inputStream, contentLength);
            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            }
            throw new RemoteAdException(
                    RemoteAdException.Reason.INVALID_RESPONSE,
                    "Backend returned HTTP " + statusCode
            );
        } catch (java.net.SocketTimeoutException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.TIMEOUT, exception.getMessage());
        } catch (IOException | IllegalArgumentException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.NETWORK, exception.getMessage());
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Nothing useful to report after request completion.
                }
            }
        }
    }

    private Socket openSocket(URL url) throws IOException {
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        int port = url.getPort() > 0 ? url.getPort() : defaultPort(protocol);
        Socket plainSocket = new Socket();
        plainSocket.connect(new InetSocketAddress(url.getHost(), port), config.getConnectTimeoutMs());
        plainSocket.setSoTimeout(config.getReadTimeoutMs());
        if ("https".equals(protocol)) {
            Socket sslSocket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(
                    plainSocket,
                    url.getHost(),
                    port,
                    true
            );
            sslSocket.setSoTimeout(config.getReadTimeoutMs());
            return sslSocket;
        }
        return plainSocket;
    }

    private static int defaultPort(String protocol) {
        return "https".equals(protocol) ? 443 : 80;
    }

    private static String hostHeader(URL url) {
        int port = url.getPort();
        if (port < 0 || port == defaultPort(url.getProtocol().toLowerCase(Locale.ROOT))) {
            return url.getHost();
        }
        return url.getHost() + ":" + port;
    }

    private static int parseStatusCode(String statusLine) throws RemoteAdException {
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, "Backend response missing status");
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, "Backend response status malformed");
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, "Backend response status malformed");
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream source = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = source.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String readRawBody(InputStream inputStream, int contentLength) throws IOException {
        if (contentLength >= 0) {
            byte[] payload = new byte[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                int read = inputStream.read(payload, offset, contentLength - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return new String(payload, 0, offset, StandardCharsets.UTF_8);
        }
        return readBody(inputStream);
    }

    private static String readAsciiLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int read;
        while ((read = inputStream.read()) != -1) {
            if (read == '\n') {
                break;
            }
            if (read != '\r') {
                output.write(read);
            }
        }
        if (read == -1 && output.size() == 0) {
            return null;
        }
        return output.toString(StandardCharsets.US_ASCII.name());
    }
}
