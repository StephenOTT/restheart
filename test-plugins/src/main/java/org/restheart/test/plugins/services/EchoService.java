/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.plugins.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.restheart.handlers.exchange.BufferedByteArrayRequest;
import org.restheart.handlers.exchange.BufferedJsonRequest;
import org.restheart.handlers.exchange.AbstractRequest;
import org.restheart.handlers.exchange.JsonResponse;
import org.restheart.handlers.exchange.Response;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BuffersUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "echo",
        description = "echoes the request",
        enabledByDefault = true,
        defaultURI = "/echo")
public class EchoService implements JsonService {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoService.class);

    /**
     * handle the request
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        var response = response().apply(exchange);
        
        JsonObject resp = new JsonObject();

        resp.addProperty("method", exchange.getRequestMethod().toString());
        resp.addProperty("URL", exchange.getRequestURL());

        if (AbstractRequest.isContentTypeJson(exchange)) {
            var request = BufferedJsonRequest.wrap(exchange);

            try {
                resp.add("content", request.readContent());
            } catch (JsonSyntaxException jse) {
                resp.addProperty("content", getTruncatedContent(BufferedByteArrayRequest.wrap(exchange)));
                resp.addProperty("note",
                        "showing up to 20 bytes of the request content");
            }
        } else {
            var request = BufferedByteArrayRequest.wrap(exchange);
            if (request.isContentTypeXml() || request.isContentTypeText()) {
                resp.addProperty("content", BuffersUtils.toString(request.getRawContent(),
                        Charset.forName("utf-8")));
            } else if (request.isContentAvailable()) {
                resp.addProperty("content", getTruncatedContent(request));
                resp.addProperty("note",
                        "showing up to 20 bytes of the request content");
            }
        }

        response.setStatusCode(HttpStatus.SC_OK);

        var qparams = new JsonObject();
        resp.add("qparams", qparams);

        exchange.getQueryParameters().forEach((name, values) -> {
            var _values = new JsonArray();

            qparams.add(name, _values);

            values.iterator().forEachRemaining(value -> {
                _values.add(value);
            });
        });

        var headers = new JsonObject();
        resp.add("headers", headers);

        exchange.getRequestHeaders().forEach(header -> {
            var _values = new JsonArray();
            headers.add(header.getHeaderName().toString(), _values);

            header.iterator().forEachRemaining(value -> {
                _values.add(value);
            });

        });

        response.setContent(resp);
    }

    private String getTruncatedContent(BufferedByteArrayRequest request)
            throws IOException {
        byte[] content = request.readContent();

        if (content == null) {
            return null;
        } else if (content.length < 1024) {
            return new String(content, StandardCharsets.UTF_8);
        } else {
            return new String(Arrays.copyOfRange(content, 0, 1023), StandardCharsets.UTF_8);
        }
    }
}