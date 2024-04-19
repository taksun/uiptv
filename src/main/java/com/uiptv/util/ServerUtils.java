package com.uiptv.util;

import com.sun.net.httpserver.HttpExchange;
import com.uiptv.api.JsonCompliant;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isNotBlank;

public class ServerUtils {

    public static final String CONTENT_TYPE_HTML = "text/html";
    public static final String CONTENT_TYPE_JAVASCRIPT = "text/javascript";
    public static final String CONTENT_TYPE_CSS = "text/css";
    public static final String CONTENT_TYPE_TEXT = "text/plain";
    public static final String CONTENT_TYPE_TS = "video/mp2t";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_M3U8 = "application/vnd.apple.mpegurl";
    public static final List<String> DOWNLOADABLE = Arrays.asList(CONTENT_TYPE_TS, CONTENT_TYPE_JAVASCRIPT, CONTENT_TYPE_CSS, CONTENT_TYPE_M3U8);

    private static Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    public static String objectToJson(List<? extends JsonCompliant> readCase) {
        if (readCase != null && !readCase.isEmpty()) {
            StringBuilder jsonArrayString = new StringBuilder("[");
            readCase.forEach(channel -> jsonArrayString.append(channel.toJson()).append(","));
            jsonArrayString.deleteCharAt(jsonArrayString.length() - 1);
            return !jsonArrayString.isEmpty() ? jsonArrayString.append("]").toString() : "[]";
        }
        return "[]";
    }

    public static String getParam(HttpExchange httpExchange, String key) {
        Map<String, String> params = queryToMap(httpExchange.getRequestURI().getQuery());
        return params.get(key);
    }

    public static void generateHtmlResponse(HttpExchange httpExchange, String response) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_HTML, null);
    }

    public static void generateJsonResponse(HttpExchange httpExchange, String response) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_JSON, null);
    }

    public static void generateJavascriptResponse(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_JAVASCRIPT, fileName);
    }

    public static void generateCssResponse(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_CSS, fileName);
    }

    public static void generateM3u8Response(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_M3U8, fileName);
    }
    public static void generateTs8Response(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_TS, fileName);
    }

    private static void generateResponse(HttpExchange httpExchange, String response, String contentType, String fileName) throws IOException {
        if (!"GET".equals(httpExchange.getRequestMethod())) {
            httpExchange.getResponseHeaders().set("Allow", "GET");
            httpExchange.sendResponseHeaders(405, -1);
            return;
        }


        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");
        httpExchange.getResponseHeaders().add("Content-Type", contentType);
        if (isNotBlank(fileName) && DOWNLOADABLE.contains(contentType)) {
            httpExchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=" + fileName);
            httpExchange.getResponseHeaders().add("Content-Transfer-Encoding", "binary");
//            httpExchange.setAttribute("Content-Type", CONTENT_TYPE_M3U8);
        }
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        httpExchange.getResponseHeaders().add("Content-length", Long.toString(responseBytes.length));
        httpExchange.sendResponseHeaders(200, responseBytes.length);

        OutputStream os = httpExchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}
