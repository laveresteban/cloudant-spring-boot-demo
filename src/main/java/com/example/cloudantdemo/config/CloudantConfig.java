package com.example.cloudantdemo.config;

import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.sdk.core.security.Authenticator;
import com.ibm.cloud.sdk.core.security.BasicAuthenticator;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.sdk.core.security.NoAuthAuthenticator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(CloudantProperties.class)
public class CloudantConfig {

    @Bean
    public Cloudant cloudant(CloudantProperties props) {
        Authenticator authenticator;
        if (StringUtils.hasText(props.apiKey())) {
            // IBM Cloudant on IBM Cloud: IAM API key
            authenticator = new IamAuthenticator.Builder()
                    .apikey(props.apiKey())
                    .build();
        } else if (StringUtils.hasText(props.username())) {
            // Apache CouchDB or Cloudant legacy basic auth
            authenticator = new BasicAuthenticator.Builder()
                    .username(props.username())
                    .password(props.password())
                    .build();
        } else {
            // Local CouchDB without authentication
            authenticator = new NoAuthAuthenticator();
        }

        Cloudant client = new Cloudant(Cloudant.DEFAULT_SERVICE_NAME, authenticator);
        client.setServiceUrl(props.url());
        return client;
    }
}
