package no.scienta.cfp;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * Serves every URL with a trailing slash as if it had none ({@code /speakers/1/} as {@code /speakers/1}). The legacy web
 * client sends such URLs (Speakers spec), and Spring stopped matching them by itself in version 6.
 */
@Configuration
public class TrailingSlashConfig {

    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        FilterRegistrationBean<UrlHandlerFilter> registration =
                new FilterRegistrationBean<>(UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
