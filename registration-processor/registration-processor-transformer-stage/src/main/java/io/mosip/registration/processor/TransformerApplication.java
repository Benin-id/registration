package io.mosip.registration.processor;

import io.mosip.registration.processor.stage.TransformerStage;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class TransformerApplication {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.scan("io.mosip.registration.processor.stage.config",
                "io.mosip.registration.processor.service","io.mosip.registration.processor.client"
                , "io.mosip.registration.processor.core.config","io.mosip.kernel.auth.defaultadapter.handler",
                "io.mosip.kernel.auth.defaultadapter.config","io.mosip.kernel.auth.defaultadapter.helper",
                "io.mosip.registration.processor.core.util","io.mosip.registration.processor.rest.client.service.impl"
                ,"io.mosip.registration.processor.rest.client.utils","io.mosip.registration.processor.stage",
                "io.mosip.registration.processor.packet.storage.config","io.mosip.registration.processor.status.config"
                ,"io.mosip.registration.processor.core.kernel.beans");
        ctx.refresh();
        TransformerStage transformerStage = ctx.getBean(TransformerStage.class);
        transformerStage.deployVerticle();
    }
}