
package io.github.microcks.observability;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtelAutoConfigurationCustomizerProvider {

   private final SpanStorageService spanStorageService;

   @Autowired
   public OtelAutoConfigurationCustomizerProvider(SpanStorageService spanStorageService) {
      this.spanStorageService = spanStorageService;
   }

   @Bean
   public AutoConfigurationCustomizerProvider otelCustomizer() {
      return p -> p.addTracerProviderCustomizer(this::configureSdkTracerProvider);
   }

   public void customize(AutoConfigurationCustomizer autoConfiguration) {
      autoConfiguration.addTracerProviderCustomizer(this::configureSdkTracerProvider);
   }

   private SdkTracerProviderBuilder configureSdkTracerProvider(SdkTracerProviderBuilder tracerProvider,
         ConfigProperties config) {
      return tracerProvider.addSpanProcessor(new CustomExplainTraceProcessor(spanStorageService));
   }
}
