package io.mosip.registration.processor.stage;


import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.abstractverticle.*;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.service.TransformerStageProcessor;
import io.vertx.core.Promise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;


@Component
/*@RefreshScope
@Service
@Configuration
@ComponentScan(basePackages = {
        "io.mosip.registration.processor.stage.config",
        "io.mosip.registration.processor.service",
        "io.mosip.registration.processor.core.config",

        "io.mosip.kernel.auth.defaultadapter.handler",
        "io.mosip.kernel.auth.defaultadapter.config",
        "io.mosip.kernel.auth.defaultadapter.helper",

        "io.mosip.registration.processor.core.util",
        "io.mosip.registration.processor.rest.client.service.impl",
        "io.mosip.registration.processor.rest.client.utils",

        "io.mosip.registration.processor.stage",
        "io.mosip.registration.processor.packet.storage.config",
        "io.mosip.registration.processor.status.config",
        "io.mosip.registration.processor.core.kernel.beans"
})*/
public class TransformerStage extends MosipVerticleAPIManager {

    public static Logger regProcLogger = RegProcessorLogger.getLogger(TransformerStage.class);
    private static final String STAGE_PROPERTY_PREFIX = "mosip.regproc.transformer-stage.";


    /** Mosip router for APIs */
    @Autowired
    private MosipRouter router;

    @Autowired
    TransformerStageProcessor transformerStageProcessor;

    /** Vertx cluster configuration file URL, which ensures all verticle joins the same cluster */
    @Value("${vertx.cluster.configuration}")
    private String clusterManagerUrl;

    /** worker pool size is the maximum number of worker threads that will be used by the Vert.x instance */
    @Value("${worker.pool.size}")
    private Integer workerPoolSize;

    /** After this time intervel, message should be considered as expired (In seconds). */
    @Value("${mosip.regproc.transformer-stage.message.expiry-time-limit}")
    private Long messageExpiryTimeLimit;

    /** The mosip event bus. */
    MosipEventBus mosipEventBus = null;

    @Override
    public void deployVerticle() {
        mosipEventBus = this.getEventBus(this, clusterManagerUrl, workerPoolSize);
        this.consumeAndSend(mosipEventBus, MessageBusAddress.TRANSFORMER_STAGE_BUS_IN, MessageBusAddress.TRANSFORMER_STAGE_BUS_OUT,
                messageExpiryTimeLimit);
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        router.setRoute(this.postUrl(getVertx(),
                MessageBusAddress.TRANSFORMER_STAGE_BUS_IN, MessageBusAddress.TRANSFORMER_STAGE_BUS_OUT));
        this.createServer(router.getRouter(), getPort());
    }

    @Override
    protected String getPropertyPrefix() {
        return STAGE_PROPERTY_PREFIX;
    }

    @Override
    public MessageDTO process(MessageDTO object) {
        return transformerStageProcessor.process(object);
    }
}
