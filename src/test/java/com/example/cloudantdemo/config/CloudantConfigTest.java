package com.example.cloudantdemo.config;

import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.sdk.core.security.BasicAuthenticator;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.sdk.core.security.NoAuthAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CloudantConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(CloudantConfig.class);

    @Test
    void apiKeySelectsIamAuthenticator() {
        runner.withPropertyValues(
                        "cloudant.url=https://account.cloudantnosqldb.appdomain.cloud",
                        "cloudant.api-key=test-key")
                .run(ctx -> {
                    Cloudant client = ctx.getBean(Cloudant.class);
                    assertThat(client.getAuthenticator()).isInstanceOf(IamAuthenticator.class);
                    assertThat(client.getServiceUrl())
                            .isEqualTo("https://account.cloudantnosqldb.appdomain.cloud");
                });
    }

    @Test
    void usernameSelectsBasicAuthenticator() {
        runner.withPropertyValues(
                        "cloudant.url=http://localhost:5984",
                        "cloudant.username=admin",
                        "cloudant.password=secret")
                .run(ctx -> assertThat(ctx.getBean(Cloudant.class).getAuthenticator())
                        .isInstanceOf(BasicAuthenticator.class));
    }

    @Test
    void apiKeyTakesPrecedenceOverUsername() {
        runner.withPropertyValues(
                        "cloudant.url=https://account.example",
                        "cloudant.api-key=test-key",
                        "cloudant.username=admin",
                        "cloudant.password=secret")
                .run(ctx -> assertThat(ctx.getBean(Cloudant.class).getAuthenticator())
                        .isInstanceOf(IamAuthenticator.class));
    }

    @Test
    void noCredentialsSelectsNoAuth() {
        runner.withPropertyValues("cloudant.url=http://localhost:5984")
                .run(ctx -> assertThat(ctx.getBean(Cloudant.class).getAuthenticator())
                        .isInstanceOf(NoAuthAuthenticator.class));
    }

    @Test
    void propertiesBindWithDefaults() {
        runner.withPropertyValues("cloudant.url=http://localhost:5984")
                .run(ctx -> {
                    CloudantProperties props = ctx.getBean(CloudantProperties.class);
                    assertThat(props.url()).isEqualTo("http://localhost:5984");
                    assertThat(props.database()).isNull();
                });
    }
}
