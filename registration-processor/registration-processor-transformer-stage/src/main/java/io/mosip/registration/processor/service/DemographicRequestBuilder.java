package io.mosip.registration.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Component
public class DemographicRequestBuilder {

    @Value("${registration.processor.transformer.mappingjson}")
    private String transformerMappingJson;

    @Value("${config.server.file.storage.uri}")
    private String configServerFileStorageURL;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode mappingJsonObject;

    public ObjectNode build(
            String registrationId,
            Map<String, String> demographicMap)
            throws Exception {

        JsonNode mappingConfig = getMappingJson();

        ObjectNode request =
                objectMapper.createObjectNode();

        processNode(
                mappingConfig,
                request,
                demographicMap,
                registrationId);

        return request;
    }

    private JsonNode getMappingJson()
            throws Exception {

        if (mappingJsonObject == null) {

            String mappingJsonString =
                    Utilities.getJson(
                            configServerFileStorageURL,
                            transformerMappingJson);

            mappingJsonObject =
                    objectMapper.readTree(
                            mappingJsonString);

        }

        return mappingJsonObject;
    }

    private void processNode(
            JsonNode mappingNode,
            ObjectNode outputNode,
            Map<String, String> demographicMap,
            String registrationId)
            throws Exception {

        Iterator<Map.Entry<String, JsonNode>> fields =
                mappingNode.fields();

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> entry =
                    fields.next();

            String targetField =
                    entry.getKey();

            JsonNode config =
                    entry.getValue();

            if (config.has("type")) {

                String value =
                        resolveValue(
                                config,
                                demographicMap,
                                registrationId);

                if (value != null
                        && !value.trim().isEmpty()) {

                    outputNode.put(
                            targetField,
                            value);

                } else {

                    outputNode.putNull(
                            targetField);
                }

                continue;
            }

            ObjectNode child =
                    objectMapper.createObjectNode();

            processNode(
                    config,
                    child,
                    demographicMap,
                    registrationId);

            outputNode.set(
                    targetField,
                    child);
        }
    }

    private String resolveValue(
            JsonNode config,
            Map<String, String> demographicMap,
            String registrationId)
            throws Exception {

        String type =
                config.path("type")
                        .asText("direct");

        if ("constant".equalsIgnoreCase(type)) {

            return config.path("value")
                    .asText(null);
        }

        if ("registration-id"
                .equalsIgnoreCase(type)) {

            return registrationId;
        }

        String source =
                config.path("source")
                        .asText(null);

        if (source == null
                || source.trim().isEmpty()) {

            return null;
        }

        String rawValue =
                demographicMap != null
                        ? demographicMap.get(source)
                        : null;

        if (rawValue == null
                || rawValue.trim().isEmpty()) {

            return null;
        }

       /* if ("direct".equalsIgnoreCase(type)) {
            return extractActualValue(rawValue);
        }*/

        if ("direct".equalsIgnoreCase(type)) {
            String value = extractActualValue(rawValue);

            if ("dateOfBirth".equals(source)
                    || "motherDateOfBirth".equals(source)
                    || "fatherDateOfBirth".equals(source)) {
                return convertToApiDate(value, source);
            }

            return value;
        }

        if ("language".equalsIgnoreCase(type)) {

            String language =
                    config.path("language")
                            .asText("fra");

            return getLanguageValue(
                    rawValue,
                    language);
        }

        if ("lookup".equalsIgnoreCase(type)) {

            String language =
                    config.path("language")
                            .asText("fra");

            String lookupValue =
                    getLanguageValue(
                            rawValue,
                            language);

            if (lookupValue == null
                    || lookupValue.trim().isEmpty()) {

                return null;
            }

            JsonNode values =
                    config.path("values");

            if (!values.has(lookupValue)) {

                return null;
            }

            return values.path(lookupValue)
                    .asText(null);
        }

        return null;
    }

    private String getLanguageValue(
            String rawValue,
            String requiredLanguage) {

        if (rawValue == null
                || rawValue.trim().isEmpty()) {

            return null;
        }

        try {

            JsonNode valueNode =
                    objectMapper.readTree(
                            rawValue);

            if (valueNode.isTextual()) {

                return valueNode.asText();
            }

            if (valueNode.isArray()) {

                for (JsonNode node : valueNode) {

                    String language =
                            node.path("language")
                                    .asText();

                    if (requiredLanguage
                            .equalsIgnoreCase(language)) {

                        String value =
                                node.path("value")
                                        .asText(null);

                        if (value == null
                                || value.trim().isEmpty()) {

                            return null;
                        }

                        return value.trim();
                    }
                }

                return null;
            }

            return rawValue.trim();

        } catch (Exception e) {

            return rawValue.trim();
        }
    }

    private String extractActualValue(String rawValue) {

        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        try {

            JsonNode node =
                    objectMapper.readTree(rawValue);

            if (node.isObject() && node.has("value")) {

                JsonNode valueNode = node.get("value");

                if (valueNode == null || valueNode.isNull()) {
                    return null;
                }

                return valueNode.asText();
            }

            if (node.isArray()) {

                for (JsonNode item : node) {

                    if (item.isObject() && item.has("value")) {

                        JsonNode valueNode =
                                item.get("value");

                        if (valueNode != null
                                && !valueNode.isNull()) {

                            return valueNode.asText();
                        }
                    }
                }
            }

            if (node.isTextual()) {
                return node.asText();
            }

        } catch (Exception e) {

        }

        return rawValue.trim();
    }

    private String convertToApiDate(String value, String source) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String dateValue = value.trim();

        DateTimeFormatter inputFormatter =
                DateTimeFormatter.ofPattern("uuuu/MM/dd")
                        .withResolverStyle(ResolverStyle.STRICT);

        try {
            LocalDate date;

            if (dateValue.matches("\\d{4}/\\d{2}/\\d{2}")) {
                date = LocalDate.parse(dateValue, inputFormatter);
            } else {
                // Also accepts dates already formatted as yyyy-MM-dd.
                date = LocalDate.parse(
                        dateValue,
                        DateTimeFormatter.ISO_LOCAL_DATE);
            }

            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);

        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date for " + source
                            + ". Expected yyyy/MM/dd or yyyy-MM-dd.",
                    e);
        }
    }
}