/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.server.filter.rest;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.owasp.encoder.Encode;

import com.ibm.fhir.config.FHIRConfigHelper;
import com.ibm.fhir.config.FHIRConfiguration;
import com.ibm.fhir.config.FHIRRequestContext;
import com.ibm.fhir.config.PropertyGroup;
import com.ibm.fhir.core.HTTPHandlingPreference;
import com.ibm.fhir.core.HTTPReturnPreference;
import com.ibm.fhir.exception.FHIRException;
import com.ibm.fhir.model.format.Format;
import com.ibm.fhir.model.generator.FHIRGenerator;
import com.ibm.fhir.model.resource.OperationOutcome;
import com.ibm.fhir.model.type.code.IssueSeverity;
import com.ibm.fhir.model.type.code.IssueType;
import com.ibm.fhir.model.util.FHIRUtil;

/**
 * This class is a servlet filter which is registered with the REST API's servlet. The main purpose of the class is to
 * log entry/exit information and elapsed time for each REST API request processed by the server.
 */
public class FHIRRestServletFilter extends HttpFilter {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger.getLogger(FHIRRestServletFilter.class.getName());

    private static String tenantIdHeaderName = null;
    private static String datastoreIdHeaderName = null;
    private static String originalRequestUriHeaderName = null;
    private static final String preferHeaderName = "Prefer";
    private static final String preferHandlingHeaderSectionName = "handling";
    private static final String preferReturnHeaderSectionName = "return";

    private static String defaultTenantId = null;
    private static final HTTPReturnPreference defaultHttpReturnPref = HTTPReturnPreference.MINIMAL;

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (log.isLoggable(Level.FINE)) {
            log.entering(this.getClass().getName(), "doFilter");
        }

        long initialTime = System.currentTimeMillis();

        String tenantId = defaultTenantId;
        String dsId = FHIRConfiguration.DEFAULT_DATASTORE_ID;
        String requestUrl = getRequestURL(request);
        String originalRequestUri = getRequestURL(request);

        // Wrap the incoming servlet request with our own implementation.
        request = new FHIRHttpServletRequestWrapper(request);
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Wrapped HttpServletRequest object...");
        }

        String t = request.getHeader(tenantIdHeaderName);
        if (t != null) {
            tenantId = t;
        }

        t = request.getHeader(datastoreIdHeaderName);
        if (t != null) {
            dsId = t;
        }

        t = getOriginalRequestURI(request);
        if (t != null) {
            originalRequestUri = t;
        }

        // Log a "request received" message.
        StringBuffer requestDescription = new StringBuffer();
        requestDescription.append("tenantId:[");
        requestDescription.append(tenantId);
        requestDescription.append("] dsId:[");
        requestDescription.append(dsId);
        requestDescription.append("] user:[");
        requestDescription.append(getRequestUserPrincipal(request));
        requestDescription.append("] method:[");
        requestDescription.append(getRequestMethod(request));
        requestDescription.append("] uri:[");
        requestDescription.append(requestUrl);
        if (!requestUrl.equals(originalRequestUri)) {
            requestDescription.append("] originalUri:[");
            requestDescription.append(originalRequestUri);
        }
        requestDescription.append("]");
        String encodedRequestDescription = Encode.forHtml(requestDescription.toString());
        log.info("Received request: " + encodedRequestDescription);

        try {
            // Create a new FHIRRequestContext and set it on the current thread.
            FHIRRequestContext context = new FHIRRequestContext(tenantId, dsId);
            // Don't try using FHIRConfigHelper before setting the context!
            FHIRRequestContext.set(context);

            context.setOriginalRequestUri(originalRequestUri);

            // Set the handling preference.
            HTTPHandlingPreference handlingPref = computeHandlingPref(request);
            context.setHandlingPreference(handlingPref);

            // Set the return preference.
            HTTPReturnPreference returnPref = computeReturnPref(request, handlingPref);
            context.setReturnPreference(returnPref);

            // Set the request headers.
            Map<String, List<String>> requestHeaders = extractRequestHeaders(request);
            context.setHttpHeaders(requestHeaders);

            // Pass the request through to the next filter in the chain.
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.log(Level.INFO, "Error while setting request context or processing request", e);

            OperationOutcome outcome = FHIRUtil.buildOperationOutcome(e, IssueType.INVALID, IssueSeverity.FATAL, false);

            if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
                HttpServletRequest httpRequest = request;
                HttpServletResponse httpResponse = response;

                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                Format format = chooseResponseFormat(httpRequest.getHeader("Accept"));
                switch (format) {
                case XML:
                    httpResponse.setContentType(com.ibm.fhir.core.FHIRMediaType.APPLICATION_FHIR_XML);
                    break;
                case JSON:
                default:
                    httpResponse.setContentType(com.ibm.fhir.core.FHIRMediaType.APPLICATION_FHIR_JSON);
                    break;
                }

                try {
                    FHIRGenerator.generator( format, false).generate(outcome, httpResponse.getWriter());

                } catch (FHIRException e1) {
                    throw new ServletException(e1);
                }
            } else {
                try {
                    FHIRGenerator.generator( Format.JSON, false).generate(outcome, response.getWriter());
                } catch (FHIRException e1) {
                    throw new ServletException(e1);
                }
            }
        } finally {
            // If possible, include the status code in the "completed" message.
            StringBuffer statusMsg = new StringBuffer();
            if (response instanceof HttpServletResponse) {
                int status = response.getStatus();
                statusMsg.append(" status:[" + status + "]");
            } else {
                statusMsg.append(" status:[unknown (non-HTTP request)]");
            }

            double elapsedSecs = (System.currentTimeMillis() - initialTime) / 1000.0;
            log.info("Completed request[" + elapsedSecs + " secs]: " + encodedRequestDescription + statusMsg.toString());

            // Remove the FHIRRequestContext from the current thread.
            FHIRRequestContext.remove();

            if (log.isLoggable(Level.FINE)) {
                log.exiting(this.getClass().getName(), "doFilter");
            }
        }
    }

    /**
     * @return a map of HTTP request headers, keyed by header name
     */
    private Map<String, List<String>> extractRequestHeaders(HttpServletRequest request) {
        // Uses LinkedHashMap just to preserve the order.
        Map<String, List<String>> requestHeaders = new LinkedHashMap<String, List<String>>();

        List<String> headerNames = Collections.list(request.getHeaderNames());
        for (String headerName : headerNames) {
            requestHeaders.put(headerName, Collections.list(request.getHeaders(headerName)));
        }

        return requestHeaders;
    }

    private HTTPHandlingPreference computeHandlingPref(ServletRequest request) throws FHIRException {
        HTTPHandlingPreference handlingPref = HTTPHandlingPreference.from(FHIRConfigHelper.getStringProperty(FHIRConfiguration.PROPERTY_DEFAULT_HANDLING, "strict"));
        boolean allowClientHandlingPref = FHIRConfigHelper.getBooleanProperty(FHIRConfiguration.PROPERTY_ALLOW_CLIENT_HANDLING_PREF, true);
        if (allowClientHandlingPref) {
            String handlingPrefString = ((HttpServletRequest) request).getHeader(preferHeaderName + ":" + preferHandlingHeaderSectionName);
            if (handlingPrefString != null && !handlingPrefString.isEmpty()) {
                try {
                    handlingPref = HTTPHandlingPreference.from(handlingPrefString);
                } catch (IllegalArgumentException e) {
                    String message = "Invalid HTTP handling preference passed in header 'Prefer': '" + handlingPrefString + "'";
                    if (handlingPref == HTTPHandlingPreference.STRICT) {
                        throw new FHIRException(message + "; use 'strict' or 'lenient'.");
                    } else {
                        log.fine(message + "; using " + handlingPref.value() + ".");
                    }
                }
            }
        }
        return handlingPref;
    }

    private HTTPReturnPreference computeReturnPref(ServletRequest request, HTTPHandlingPreference handlingPref) throws FHIRException {
        HTTPReturnPreference returnPref = defaultHttpReturnPref;
        String returnPrefString = ((HttpServletRequest) request).getHeader(preferHeaderName + ":" + preferReturnHeaderSectionName);
        if (returnPrefString != null && !returnPrefString.isEmpty()) {
            try {
                returnPref = HTTPReturnPreference.from(returnPrefString);
            } catch (IllegalArgumentException e) {
                String message = "Invalid HTTP return preference passed in header 'Prefer': '" + returnPrefString + "'";
                if (handlingPref == HTTPHandlingPreference.STRICT) {
                    throw new FHIRException(message + "; use 'minimal', 'representation' or 'OperationOutcome'.");
                } else {
                    log.fine(message + "; using " + returnPref.value() + ".");
                }
            }
        }
        return returnPref;
    }

    private Format chooseResponseFormat(String acceptableContentTypes) {
        if (acceptableContentTypes.contains(com.ibm.fhir.core.FHIRMediaType.APPLICATION_FHIR_JSON) ||
                acceptableContentTypes.contains(MediaType.APPLICATION_JSON)) {
            return Format.JSON;
        } else if (acceptableContentTypes.contains(com.ibm.fhir.core.FHIRMediaType.APPLICATION_FHIR_XML) ||
                acceptableContentTypes.contains(MediaType.APPLICATION_XML)) {
            return Format.XML;
        } else {
            return Format.JSON;
        }
    }

    /**
     * Retrieves the username associated with the HTTP request.
     */
    private String getRequestUserPrincipal(ServletRequest request) {
        String user = null;

        if (request instanceof HttpServletRequest) {
            Principal principal = ((HttpServletRequest) request).getUserPrincipal();
            if (principal != null) {
                user = principal.getName();
            }
        }
        return (user != null ? user : "<unauthenticated>");
    }

    /**
     * Returns the HTTP method name associated with the specified request.
     */
    private String getRequestMethod(ServletRequest request) {
        String method = null;

        if (request instanceof HttpServletRequest) {
            method = ((HttpServletRequest) request).getMethod();
        }
        return (method != null ? method : "<unknown>");
    }

    /**
     * Returns the full request URL (i.e. http://host:port/a/path?queryString) associated with the specified request.
     */
    private String getRequestURL(HttpServletRequest request) {
        StringBuffer sb = request.getRequestURL();
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            sb.append("?");
            sb.append(queryString);
        }
        return sb.toString();
    }

    /**
     * Get the original request URL from either the HttpServletRequest or a configured Header (in case of re-writing proxies).
     *
     * @param request
     * @return String The complete request URI
     */
    private String getOriginalRequestURI(HttpServletRequest request) {
        String requestUri = null;

        // First, check the configured header for the original request URI (in case any proxies have overwritten the user-facing URL)
        if (originalRequestUriHeaderName != null) {
            requestUri = request.getHeader(originalRequestUriHeaderName);
            if (requestUri != null && !requestUri.isEmpty()) {
                // Try to parse it as a URI to ensure its valid
                try {
                    URI originalRequestUri = new URI(requestUri);
                    // If its not absolute, then construct an absolute URI (or else JAX-RS will append the path to the current baseUri)
                    if (!originalRequestUri.isAbsolute()) {
                        requestUri = UriBuilder.fromUri(getRequestURL(request))
                            .replacePath(originalRequestUri.getPath()).build().toString();
                    }
                } catch (Exception e) {
                    log.log(Level.WARNING, "Error while computing the original request URI", e);
                    requestUri = null;
                }
            }
        }

        // If there was no configured header or the header wasn't present, construct it from the HttpServletRequest
        if (requestUri == null || requestUri.isEmpty()) {
            StringBuilder requestUriBuilder = new StringBuilder(request.getRequestURL());
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                requestUriBuilder.append("?").append(queryString);
            }
            requestUri = requestUriBuilder.toString();
        }
        return requestUri;
    }

    @Override
    public void destroy() {
        // Nothing to do here...
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            PropertyGroup config = FHIRConfiguration.getInstance().loadConfiguration();
            if (config == null) {
                throw new IllegalStateException("No FHIRConfiguration was found");
            }

            tenantIdHeaderName = config.getStringProperty(FHIRConfiguration.PROPERTY_TENANT_ID_HEADER_NAME,
                    FHIRConfiguration.DEFAULT_TENANT_ID_HEADER_NAME);
            log.info("Configured tenant-id header name is: " +  tenantIdHeaderName);

            datastoreIdHeaderName = config.getStringProperty(FHIRConfiguration.PROPERTY_DATASTORE_ID_HEADER_NAME,
                    FHIRConfiguration.DEFAULT_DATASTORE_ID_HEADER_NAME);
            log.info("Configured datastore-id header name is: " +  datastoreIdHeaderName);

            originalRequestUriHeaderName = config.getStringProperty(FHIRConfiguration.PROPERTY_ORIGINAL_REQUEST_URI_HEADER_NAME,
                    null);
            log.info("Configured original-request-uri header name is: " +  datastoreIdHeaderName);

            defaultTenantId =
                    config.getStringProperty(FHIRConfiguration.PROPERTY_DEFAULT_TENANT_ID, FHIRConfiguration.DEFAULT_TENANT_ID);
            log.info("Configured default tenant-id value is: " +  defaultTenantId);
        } catch (Exception e) {
            throw new ServletException("Servlet filter initialization error.", e);
        }
    }
}
