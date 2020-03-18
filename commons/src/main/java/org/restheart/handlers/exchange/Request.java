/*
 * RESTHeart Common
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.handlers.exchange;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T>
 */
public abstract class Request<T> extends AbstractExchange<T> {

    public static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    public static final String MULTIPART = "multipart/form-data";

    // other constants
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";

    private static final AttachmentKey<PipelineBranchInfo> PIPELINE_BRANCH_INFO_KEY
            = AttachmentKey.create(PipelineBranchInfo.class);

    private static final AttachmentKey<Long> START_TIME_KEY
            = AttachmentKey.create(Long.class);

    private static final AttachmentKey<Map<String, List<String>>> XFORWARDED_HEADERS
            = AttachmentKey.create(Map.class);

    protected Request(HttpServerExchange exchange) {
        super(exchange);
    }

    public static String getContentType(HttpServerExchange exchange) {
        return exchange.getRequestHeaders()
                .getFirst(Headers.CONTENT_TYPE);
    }

    private static METHOD selectMethod(HttpString _method) {
        if (Methods.GET.equals(_method)) {
            return METHOD.GET;
        } else if (Methods.POST.equals(_method)) {
            return METHOD.POST;
        } else if (Methods.PUT.equals(_method)) {
            return METHOD.PUT;
        } else if (Methods.DELETE.equals(_method)) {
            return METHOD.DELETE;
        } else if (PATCH.equals(_method.toString())) {
            return METHOD.PATCH;
        } else if (Methods.OPTIONS.equals(_method)) {
            return METHOD.OPTIONS;
        } else {
            return METHOD.OTHER;
        }
    }

    /**
     *
     * @return the request method
     */
    public METHOD getMethod() {
        return selectMethod(getWrappedExchange().getRequestMethod());
    }

    /**
     * @return the request ContentType
     */
    @Override
    public String getContentType() {
        return getContentType(getWrappedExchange());
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setContentType(String responseContentType) {
        getWrappedExchange().getRequestHeaders().put(Headers.CONTENT_TYPE,
                responseContentType);
    }

    /**
     * sets Content-Type=application/json
     */
    public void setContentTypeAsJson() {
        setContentType("application/json");
    }

    protected void setContentLength(int length) {
        wrapped.getRequestHeaders().put(Headers.CONTENT_LENGTH, length);
    }

    /**
     * @return the requestStartTime
     */
    public Long getStartTime() {
        return getWrappedExchange().getAttachment(START_TIME_KEY);
    }

    /**
     * @param requestStartTime the requestStartTime to set
     */
    public void setStartTime(Long requestStartTime) {
        getWrappedExchange().putAttachment(START_TIME_KEY, requestStartTime);
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return getWrappedExchange().getSecurityContext().getAuthenticatedAccount();
    }

    /**
     * Add the header X-Forwarded-[key] to the proxied request; use it to pass
     * to the bbackend information otherwise lost proxying the request.
     *
     * @param key
     * @param value
     */
    public void addXForwardedHeader(String key, String value) {
        if (wrapped.getAttachment(XFORWARDED_HEADERS) == null) {
            wrapped.putAttachment(XFORWARDED_HEADERS, new LinkedHashMap<>());

        }

        var values = wrapped.getAttachment(XFORWARDED_HEADERS).get(key);

        if (values == null) {
            values = new ArrayList<>();
            wrapped.getAttachment(XFORWARDED_HEADERS).put(key, values);
        }

        values.add(value);
    }

    public Map<String, List<String>> getXForwardedHeaders() {
        return getWrappedExchange().getAttachment(XFORWARDED_HEADERS);
    }

    /**
     *
     * @return the PipelineBranchInfo that allows to know which pipeline branch
     * (service, proxy or static resource) is handling the exchange
     */
    public PipelineBranchInfo getPipelineBranchInfo() {
        return getWrappedExchange().getAttachment(PIPELINE_BRANCH_INFO_KEY);
    }

    /**
     * @param pipelineBranchInfo the requestStartTime to set
     */
    public void setPipelineBranchInfo(PipelineBranchInfo pipelineBranchInfo) {
        getWrappedExchange().putAttachment(PIPELINE_BRANCH_INFO_KEY, pipelineBranchInfo);
    }

    /**
     * helper method to check if the request content is Json
     *
     * @param exchange
     * @return true if Content-Type request header is application/json
     */
    public static boolean isContentTypeJson(HttpServerExchange exchange) {
        return "application/json".equals(getContentType(exchange));
    }

    public static boolean isContentTypeFormOrMultipart(HttpServerExchange exchange) {
        return getContentType(exchange) != null
                && (getContentType(exchange).startsWith(FORM_URLENCODED)
                || getContentType(exchange).startsWith(MULTIPART));
    }

    public boolean isContentTypeFormOrMultipart() {
        return isContentTypeFormOrMultipart(wrapped);
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return getMethod() == METHOD.DELETE;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return getMethod() == METHOD.GET;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return getMethod() == METHOD.OPTIONS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return getMethod() == METHOD.PATCH;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return getMethod() == METHOD.POST;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return getMethod() == METHOD.PUT;
    }
}
