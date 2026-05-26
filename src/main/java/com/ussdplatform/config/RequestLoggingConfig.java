package com.ussdplatform.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@Slf4j
public class RequestLoggingConfig {

    /**
     * Logs every incoming HTTP request and outgoing response.
     */
    @Bean
    public OncePerRequestFilter requestResponseLoggingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                long start = System.currentTimeMillis();

                log.info(">>> {} {} | IP: {} | Content-Type: {}",
                        request.getMethod(),
                        request.getRequestURI(),
                        request.getRemoteAddr(),
                        request.getContentType());

                chain.doFilter(request, response);

                long duration = System.currentTimeMillis() - start;
                log.info("<<< {} {} | Status: {} | {}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration);
            }
        };
    }

    @Bean
    public CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(1000);
        filter.setIncludeHeaders(false);
        filter.setAfterMessagePrefix("REQUEST: ");
        return filter;
    }
}
