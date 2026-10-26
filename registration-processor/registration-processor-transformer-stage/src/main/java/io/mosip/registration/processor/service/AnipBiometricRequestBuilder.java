package io.mosip.registration.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.registration.processor.packet.storage.dto.Document;
import io.mosip.registration.processor.packet.storage.utils.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.util.*;

@Component
public class AnipBiometricRequestBuilder {

    private static final Logger log =
            LoggerFactory.getLogger(AnipBiometricRequestBuilder.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnipFaceConverter anipFaceConverter;

    @Autowired
    private AnipDocumentRequestBuilder anipDocumentRequestBuilder;

    @Autowired
    private AnipFingerprintConverter anipFingerprintConverter;

    @Value("${registration.processor.transformer.mappingjson}")
    private String mappingName;

    @Value("${config.server.file.storage.uri}")
    private String configUri;

    private volatile JsonNode mappingCache;

    public ObjectNode build(BiometricRecord record, Map<String, Document> documentMap
    ) throws Exception {

        JsonNode config = getMappingJson().path("biometrie");

        if (!config.isObject()) {
            throw new IllegalStateException(
                    "Missing biometrie mapping"
            );
        }

        ObjectNode result = objectMapper.createObjectNode();

        // Step 1: Extract and convert face biometric

        JsonNode photoMapping = config.path("photo");

        if (!photoMapping.isObject()) {
            throw new IllegalStateException(
                    "Missing photo mapping"
            );
        }
        BIR face = findBiometric(
                record,
                photoMapping.path("biometricType").asText("FACE"),
                null
        );
        if (face == null
                || face.getBdb() == null
                || face.getBdb().length == 0) {

            throw new IllegalStateException(
                    "A real face image is required for ANIP enrolment"
            );
        }

        String faceBase64 =
                anipFaceConverter.convertIsoFaceToBase64(
                        face.getBdb()
                );

        result.putObject("photo")
                .put("data_base64", faceBase64);

        log.debug("ANIP face converted successfully");

        // STEP 2: CONTEXT IMAGES / DOCUMENTS
        ArrayNode contextImages =
                anipDocumentRequestBuilder.buildImagesContexte(
                        documentMap
                );

        // Keep this field immediately after photo.
        result.set("images_contexte", contextImages);

        log.info(
                "ANIP context images prepared; count={}",
                contextImages.size()
        );

        // Step 2: Prepare fingerprints

        ArrayNode fingers = result.putArray("empreintes");

        JsonNode configured = config.path("empreintes");

        if (!configured.isArray()) {
            throw new IllegalStateException(
                    "Missing empreintes mapping array"
            );
        }

        if (configured.size() > 10) {
            throw new IllegalStateException(
                    "ANIP supports at most 10 fingerprints"
            );
        }

        Set<String> positions = new HashSet<>();

        // Step 3: Extract and convert each fingerprint to WSQ

        for (JsonNode entry : configured) {

            String position =
                    entry.path("doigt").asText("");

            if (position.isBlank() || !positions.add(position)) {
                throw new IllegalStateException(
                        "Duplicate or blank finger position in mapping"
                );
            }

            String biometricType =
                    entry.path("biometricType").asText("FINGER");

            JsonNode subtypes = entry.path("subtypes");

            BIR finger = findBiometric(
                    record,
                    biometricType,
                    subtypes
            );

            if (finger == null
                    || finger.getBdb() == null
                    || finger.getBdb().length == 0) {

                log.warn(
                        "Fingerprint missing for configured position={}",
                        position
                );

                continue;
            }

            String wsqBase64 =
                    anipFingerprintConverter.convertToWsqBase64(
                            finger.getBdb()
                    );



            ObjectNode fingerprint =
                    fingers.addObject();

            fingerprint.put("doigt", position);
            fingerprint.put("data_base64", wsqBase64);

            log.debug(
                    "ANIP fingerprint converted successfully; position={}",
                    position
            );
        }
        log.info(
                "ANIP biometric request prepared; fingerprintCount={}, photoPresent={}",
                fingers.size(),
                result.hasNonNull("photo")
        );

        return result;
    }

    private JsonNode getMappingJson() throws Exception {

        JsonNode local = mappingCache;

        if (local == null) {

            synchronized (this) {

                local = mappingCache;

                if (local == null) {

                    String text = Utilities.getJson(
                            configUri,
                            mappingName
                    );

                    if (text == null || text.isBlank()) {
                        throw new IllegalStateException(
                                "Empty ANIP mapping configuration"
                        );
                    }

                    local = objectMapper.readTree(text);

                    mappingCache = local;

                    log.info(
                            "ANIP biometric mapping loaded successfully"
                    );
                }
            }
        }

        return local;
    }

    private BIR findBiometric(
            BiometricRecord biometricRecord,
            String expectedType,
            JsonNode expectedSubtypes) {

        if (biometricRecord == null
                || biometricRecord.getSegments() == null) {
            return null;
        }

        for (BIR bir : biometricRecord.getSegments()) {

            if (bir == null || bir.getBdbInfo() == null) {
                continue;
            }

            if (!matchesType(bir, expectedType)) {
                continue;
            }

            if (expectedSubtypes == null
                    || !expectedSubtypes.isArray()
                    || expectedSubtypes.isEmpty()) {

                return bir;
            }

            if (matchesSubtypes(bir, expectedSubtypes)) {
                return bir;
            }
        }

        return null;
    }

    private boolean matchesType(
            BIR bir,
            String expectedType) {

        if (expectedType == null
                || expectedType.trim().isEmpty()) {
            return false;
        }

        if (bir == null
                || bir.getBdbInfo() == null
                || bir.getBdbInfo().getType() == null) {
            return false;
        }

        for (Object type : bir.getBdbInfo().getType()) {

            if (type != null
                    && expectedType.equalsIgnoreCase(
                    String.valueOf(type)
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesSubtypes(
            BIR bir,
            JsonNode expectedSubtypes) {

        if (bir == null || bir.getBdbInfo() == null) {
            return false;
        }

        List<String> actualSubtypes =
                bir.getBdbInfo().getSubtype();

        if (actualSubtypes == null
                || actualSubtypes.isEmpty()) {
            return false;
        }

        for (JsonNode expected : expectedSubtypes) {

            String expectedValue = expected.asText();

            boolean found = false;

            for (String actual : actualSubtypes) {

                if (actual != null
                        && expectedValue.equalsIgnoreCase(actual)) {

                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }
}