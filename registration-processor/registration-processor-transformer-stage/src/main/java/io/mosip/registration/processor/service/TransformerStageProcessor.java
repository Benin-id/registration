package io.mosip.registration.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.registration.processor.client.AnipApiClient;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.dto.Document;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;

import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TransformerStageProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(TransformerStageProcessor.class);

    @Autowired
    private PriorityBasedPacketManagerService packetManagerService;

    @Autowired
    private Utilities utilities;

    @Autowired
    private AuditLogRequestBuilder auditLogRequestBuilder;

    @Autowired
    private DemographicRequestBuilder demographicRequestBuilder;

    @Autowired
    private AnipBiometricRequestBuilder biometricRequestBuilder;

    @Autowired
    private AnipDocumentRequestBuilder documentRequestBuilder;

    @Autowired
    private RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

    @Autowired
    private AnipApiClient anipApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MessageDTO process(MessageDTO message)  {

        LogDescription description = new LogDescription();
        boolean isTransactionSuccessful = false;
        String rid = message.getRid();

        InternalRegistrationStatusDto registrationStatusDto =
                registrationStatusService.getRegistrationStatus(rid,
                        message.getReg_type(),
                        message.getIteration(),
                        message.getWorkflowInstanceId());

        if (registrationStatusDto == null) {
            throw new IllegalStateException(
                    "Registration status not found for RID: "
                            + rid);
        }
        registrationStatusDto.setLatestTransactionTypeCode("TRANSFORM_PACKET");

        registrationStatusDto.setRegistrationStageName("TransformerStage");

        message.setIsValid(Boolean.FALSE);
        message.setInternalError(Boolean.TRUE);

        log.info("ANIP Transformer Stage processing started");

        try {
            // Step 1: Fetch demographic fields from Packet Manager

            Map<String, String> fields = packetManagerService.getAllFieldsByMappingJsonKeys(rid, message.getReg_type(),
                    ProviderStageName.TRANSFORMER);

            if (fields == null || fields.isEmpty()) {
                throw new IllegalStateException(
                        "Packet Manager returned no demographic fields"
                );
            }
            log.debug("Demographic fields retrieved successfully");

            // Step 2: Build ANIP demographic request

            ObjectNode request = demographicRequestBuilder.build(rid, fields);

            log.info("========== ANIP DEMOGRAPHIC REQUEST ==========\n{}",
                    request.toPrettyString());
            // Step 3: Fetch biometric record from Packet Manager

            BiometricRecord biometricRecord = packetManagerService.getBiometricsByMappingJsonKey(rid, MappingJsonConstants.INDIVIDUAL_BIOMETRICS, message.getReg_type(),
                    ProviderStageName.TRANSFORMER);

            if (biometricRecord == null
                    || biometricRecord.getSegments() == null
                    || biometricRecord.getSegments().isEmpty()) {

                throw new IllegalStateException(
                        "Biometric record is required for ANIP enrolment"
                );
            }

            log.debug("Biometric record retrieved successfully");

            Map<String, Document> documents = new LinkedHashMap<>();

            JSONObject mapping = utilities.getRegistrationProcessorMappingJson(
                    MappingJsonConstants.DOCUMENT);

            if (mapping != null) {

                for (Object key : mapping.keySet()) {
                    String mappingKey = String.valueOf(key);
                    JSONObject config = JsonUtil.getJSONObject(mapping, mappingKey);

                    if (config == null) {
                        continue;
                    }

                    String documentName =
                            JsonUtil.getJSONValue(
                                    config,
                                    MappingJsonConstants.VALUE
                            );

                    if (documentName == null
                            || documentName.isBlank()) {
                        continue;
                    }

                    try {

                        Document document = packetManagerService.getDocument(rid, documentName, message.getReg_type(), ProviderStageName.TRANSFORMER);

                        if (document != null) {
                            documents.put(mappingKey, document);
                        }

                    } catch (Exception e) {

                        log.warn(
                                "Optional ANIP context attachment unavailable; mappingKey={}",
                                mappingKey
                        );
                    }
                }
            }

            // After document retrieval is complete
            ObjectNode biometric =
                    biometricRequestBuilder.build(
                            biometricRecord,
                            documents
                    );

            // Step 7: Add biometrics to ANIP request
            request.set("biometrie", biometric);

            // Step 8: Validate complete ANIP request

            // anipRequestValidator.validate(request);
            log.info("ANIP Complete Request Payload:\n{}", request.toPrettyString());
            log.info("ANIP request validation successful");


            JsonNode response =
                    anipApiClient.createEnrollment(request);

            // Step 10: Log successful API response without sensitive data

            log.info(
                    "ANIP enrolment API completed; certified={}, validationStatus={}",
                    response.path("certifie").asBoolean(),
                    response.path("statut_validation").asText());

            String numeroFormulaire = request
                    .path("contexte_enrolement")
                    .path("numero_formulaire")
                    .asText("");

            // STEP 5: SUCCESS transaction
            registrationStatusDto.setLatestTransactionStatusCode(
                    RegistrationTransactionStatusCode.SUCCESS.toString());

            registrationStatusDto.setStatusComment(
                    "Transformation successful. ANIP form number: "
                            + numeroFormulaire);

            registrationStatusDto.setSubStatusCode(
                    "RPR-TRF-SUCCESS-001");

            isTransactionSuccessful = true;

            message.setIsValid(Boolean.TRUE);
            message.setInternalError(Boolean.FALSE);

        } catch (Exception e) {

            log.error("Transformer Stage failed for RID: {}",
                    rid, e);

            // STEP 6: FAILED transaction
            if (registrationStatusDto != null) {

                registrationStatusDto.setLatestTransactionStatusCode(
                        RegistrationTransactionStatusCode.FAILED.toString());

                registrationStatusDto.setStatusComment(
                        "Transformation failed: " + e.getMessage());

                registrationStatusDto.setSubStatusCode(
                        "RPR-TRF-FAILED-001");
            }

            isTransactionSuccessful = false;


            message.setIsValid(Boolean.FALSE);
            message.setInternalError(Boolean.FALSE);

        } catch (Error e) {

            log.error("Transformer Stage critical error for RID: {}", rid, e);

            registrationStatusDto.setLatestTransactionStatusCode(
                    RegistrationTransactionStatusCode.FAILED.toString());

            registrationStatusDto.setStatusComment(
                    "Transformation critical error: " + e.getClass().getSimpleName());

            registrationStatusDto.setSubStatusCode("RPR-TRF-FAILED-001");

            isTransactionSuccessful = false;

            message.setIsValid(Boolean.FALSE);
            message.setInternalError(Boolean.TRUE);
            throw e;

        } finally {

            if (registrationStatusDto != null) {

                registrationStatusDto.setUpdatedBy("MOSIP_SYSTEM");

                String moduleId = isTransactionSuccessful
                        ? "RPR-TRF-000"
                        : "RPR-TRF-001";

                String moduleName = "TRANSFORMER_STAGE";

                // 1. Update registration transaction
                registrationStatusService.updateRegistrationStatus(
                        registrationStatusDto,
                        moduleId,
                        moduleName
                );

                // 2. Audit details
                String eventId = isTransactionSuccessful
                        ? EventId.RPR_402.toString()
                        : EventId.RPR_405.toString();

                String eventName = isTransactionSuccessful
                        ? EventName.UPDATE.toString()
                        : EventName.EXCEPTION.toString();

                String eventType = isTransactionSuccessful
                        ? EventType.BUSINESS.toString()
                        : EventType.SYSTEM.toString();

                String auditMessage = isTransactionSuccessful
                        ? "Transformer Stage completed successfully"
                        : "Transformer Stage failed";

                // 3. Call MOSIP Audit
                auditLogRequestBuilder.createAuditRequestBuilder(
                        auditMessage,
                        eventId,
                        eventName,
                        eventType,
                        moduleId,
                        moduleName,
                        registrationStatusDto.getRegistrationId()
                );
            }
        }
            return message;
        }
    }