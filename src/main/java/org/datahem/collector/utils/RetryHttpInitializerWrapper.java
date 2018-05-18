package org.datahem.collector.utils;

/*-
 * ========================LICENSE_START=================================
 * DataHem
 * %%
 * Copyright (C) 2018 Robert Sahlin and MatHem Sverige AB
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * =========================LICENSE_END==================================
 */



import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Sleeper;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * RetryHttpInitializerWrapper will automatically retry upon RPC
 * failures, preserving the auto-refresh behavior of the Google
 * Credentials.
 */
public class RetryHttpInitializerWrapper implements HttpRequestInitializer {

    /**
     * A private logger.
     */
    private static final Logger LOG =
            Logger.getLogger(RetryHttpInitializerWrapper.class.getName());

    /**
     * One minutes in miliseconds.
     */
    private static final int ONEMINITUES = 60000;

    /**
     * Intercepts the request for filling in the "Authorization"
     * header field, as well as recovering from certain unsuccessful
     * error codes wherein the Credential must refresh its token for a
     * retry.
     */
    private final Credential wrappedCredential;

    /**
     * A sleeper; you can replace it with a mock in your test.
     */
    private final Sleeper sleeper;

    /**
     * A constructor.
     *
     * @param wrappedCredential Credential which will be wrapped and
     * used for providing auth header.
     */
    public RetryHttpInitializerWrapper(final Credential wrappedCredential) {
        this(wrappedCredential, Sleeper.DEFAULT);
    }

    /**
     * A protected constructor only for testing.
     *
     * @param wrappedCredential Credential which will be wrapped and
     * used for providing auth header.
     * @param sleeper Sleeper for easy testing.
     */
    RetryHttpInitializerWrapper(
            final Credential wrappedCredential, final Sleeper sleeper) {
        this.wrappedCredential = Preconditions.checkNotNull(wrappedCredential);
        this.sleeper = sleeper;
    }

    /**
     * Initializes the given request.
     */
    @Override
    public final void initialize(final HttpRequest request) {
        request.setReadTimeout(2 * ONEMINITUES); // 2 minutes read timeout
        final HttpUnsuccessfulResponseHandler backoffHandler =
                new HttpBackOffUnsuccessfulResponseHandler(
                        new ExponentialBackOff())
                        .setSleeper(sleeper);
        request.setInterceptor(wrappedCredential);
        request.setUnsuccessfulResponseHandler(
                new HttpUnsuccessfulResponseHandler() {
                    @Override
                    public boolean handleResponse(
                            final HttpRequest request,
                            final HttpResponse response,
                            final boolean supportsRetry) throws IOException {
                        if (wrappedCredential.handleResponse(
                                request, response, supportsRetry)) {
                            // If credential decides it can handle it,
                            // the return code or message indicated
                            // something specific to authentication,
                            // and no backoff is desired.
                            return true;
                        } else if (backoffHandler.handleResponse(
                                request, response, supportsRetry)) {
                            // Otherwise, we defer to the judgement of
                            // our internal backoff handler.
                            LOG.info("Retrying "
                                    + request.getUrl().toString());
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
        request.setIOExceptionHandler(
                new HttpBackOffIOExceptionHandler(new ExponentialBackOff())
                        .setSleeper(sleeper));
    }
}
