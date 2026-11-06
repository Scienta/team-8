package no.scienta.cfp;

import java.util.Date;
import java.util.Map;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.CharacterEncodingFilter;

/**
 * Keeps error responses as they were before Spring Boot 4, for clients that read them: the {@code timestamp} written
 * with an explicit {@code +00:00} offset (Jackson 3 writes {@code Z}), and {@code charset=UTF-8} on the content type.
 */
@Configuration
public class LegacyErrorResponses {

    @Bean
    public DefaultErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
                Map<String, Object> attributes = super.getErrorAttributes(request, options);
                Object timestamp = attributes.get("timestamp");
                if (timestamp instanceof Date) {
                    attributes.put("timestamp", Utils.reportingTimestamp(((Date) timestamp).getTime()));
                }
                return attributes;
            }
        };
    }

    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> errorCharacterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter("UTF-8", true) {
            @Override
            protected boolean shouldNotFilterErrorDispatch() {
                return false;
            }
        };
        FilterRegistrationBean<CharacterEncodingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(DispatcherType.ERROR);
        return registration;
    }
}
