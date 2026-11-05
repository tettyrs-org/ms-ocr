package org.tettyrs.msocr.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Tracing OpenTelemetry lewat exporter OTLP/HTTP yang dipasang di kode, bukan lewat extension Quarkus.
 * Aktif hanya bila OCR_TRACES_EXPORTER=otlp. Trace dari api dilanjutkan lewat traceparent di payload job.
 * Span hanya memuat metadata. Isi dokumen tidak pernah masuk ke span.
 */
@ApplicationScoped
public class TracingSetup {

    private static final Logger LOG = Logger.getLogger(TracingSetup.class);

    @ConfigProperty(name = "ocr.traces.exporter", defaultValue = "none")
    String exporter;

    @ConfigProperty(name = "ocr.traces.endpoint", defaultValue = "http://localhost:4318/v1/traces")
    String endpoint;

    @ConfigProperty(name = "ocr.service.name", defaultValue = "ms-ocr")
    String namaService;

    void mulai(@Observes StartupEvent event) {
        if (!"otlp".equalsIgnoreCase(exporter)) {
            LOG.info("tracing nonaktif (OCR_TRACES_EXPORTER bukan otlp)");
            return;
        }
        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), namaService)));
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(
                        OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build()).build())
                .build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
        LOG.infof("tracing aktif, ekspor OTLP/HTTP ke %s", endpoint);
    }
}
