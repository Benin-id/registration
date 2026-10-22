package io.mosip.registration.processor.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;

@Component
public class AnipApiClient {

    private static final Logger log =
            LoggerFactory.getLogger(AnipApiClient.class);

    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;

    public AnipApiClient(
            ObjectMapper mapper,
            @Value("${mosip.regproc.transformer.anip.base-url}")
            String baseUrl,
            @Value("${mosip.regproc.transformer.anip.api-key}")
            String apiKey,
            @Value("${mosip.regproc.transformer.anip.connect-timeout-ms:5000}")
            int connectTimeoutMs,
            @Value("${mosip.regproc.transformer.anip.read-timeout-ms:120000}")
            int readTimeoutMs) {

        this.mapper = mapper;
        this.baseUrl = baseUrl.trim();
        this.apiKey = apiKey.trim();

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        this.restTemplate = new RestTemplate(factory);
    }

    public JsonNode createEnrollment(
            JsonNode requestBody) throws Exception {

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANIP gateway URL or API key is missing");
        }

        if (requestBody == null || requestBody.isNull()) {
            throw new IllegalArgumentException(
                    "ANIP request body is missing");
        }

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(
                MediaType.APPLICATION_JSON));

        headers.set("Login", "MOSIP");
        headers.set("apiKEY", apiKey);
        headers.set("UX_SERVICE", "identites_telecharger");

        String requestJson =
                mapper.writeValueAsString(requestBody);

        HttpEntity<String> entity =
                new HttpEntity<>(requestJson, headers);

        log.info("Sending ANIP gateway enrolment request");

        ResponseEntity<String> response;

        try {
            response = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

        } catch (HttpStatusCodeException e) {

            log.error(
                    "ANIP gateway HTTP error; status={}",
                    e.getRawStatusCode());

            throw new IllegalStateException(
                    "ANIP gateway HTTP error: "
                            + e.getRawStatusCode()
                            + ", response: "
                            + safeErrorDetails(e.getResponseBodyAsString()),
                    e);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "ANIP gateway returned HTTP "
                            + response.getStatusCodeValue());
        }

        String responseBody = response.getBody();

        if (responseBody == null
                || responseBody.isBlank()) {

            throw new IllegalStateException(
                    "ANIP gateway returned empty response");
        }

        JsonNode body;

        try {
            body = mapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "ANIP gateway returned invalid JSON", e);
        }

        // Gateway success indicator.
       /* if (!"1".equals(body.path("etat").asText())) {
            String message =
                    body.path("message").asText("Unknown error");

            String errorCode = body.path("Data")
                    .path("detail")
                    .path("code")
                    .asText("UNKNOWN");

            throw new IllegalStateException(
                    "ANIP gateway rejected request; code="
                            + errorCode
                            + ", message=" + message);
        }*/

        if (!"1".equals(body.path("etat").asText())) {

            throw new IllegalStateException(
                    "ANIP gateway rejected request; "
                            + safeErrorDetails(responseBody)
            );
        }


        // Gateway wraps the ANIP response inside Data.
        JsonNode data = body.path("Data");

        if (!"valid".equalsIgnoreCase(
                data.path("statut_validation").asText())) {

            throw new IllegalStateException(
                    "ANIP validation status is not valid: "
                            + data.path("statut_validation").asText());
        }

        JsonNode file = data.path("fichier");

        String filename =
                file.path("filename").asText();

        if (filename.isBlank()) {
            throw new IllegalStateException(
                    "ANIP NST filename is missing");
        }

        if (!"base64".equalsIgnoreCase(
                file.path("encoding").asText())) {

            throw new IllegalStateException(
                    "ANIP NST encoding is not Base64");
        }

        String base64 =
                file.path("data_base64").asText();

        if (base64.isBlank()) {
            throw new IllegalStateException(
                    "ANIP NST Base64 data is missing");
        }

        byte[] nstBytes;

        try {
            nstBytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ANIP returned invalid NST Base64", e);
        }

        long expectedSize =
                file.path("size").asLong(-1);

        if (nstBytes.length == 0
                || nstBytes.length != expectedSize) {

            throw new IllegalStateException(
                    "ANIP NST size mismatch; expected="
                            + expectedSize
                            + ", actual=" + nstBytes.length);
        }

        byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(nstBytes);

        StringBuilder actualHash =
                new StringBuilder(digest.length * 2);

        for (byte b : digest) {
            actualHash.append(
                    String.format("%02x", b & 0xff));
        }

        String expectedHash =
                file.path("sha256").asText();

        if (!actualHash.toString()
                .equalsIgnoreCase(expectedHash)) {

            throw new IllegalStateException(
                    "ANIP NST SHA-256 mismatch");
        }

        log.info(
                "ANIP NST generated successfully; filename={}, bytes={}, certified={}",
                filename,
                nstBytes.length,
                data.path("certifie").asBoolean());

        return body;
    }

/*    private String safeErrorDetails(String responseBody) {
        try {
            JsonNode error = mapper.readTree(responseBody);

            String code = error.path("Data")
                    .path("detail")
                    .path("code")
                    .asText("UNKNOWN");

            String message = error.path("message")
                    .asText("Gateway error");

            return "code=" + code + ", message=" + message;

        } catch (Exception e) {
            return "Unable to parse gateway error";
        }
    }*/

    private String safeErrorDetails(String responseBody) {
        try {
            JsonNode error = mapper.readTree(responseBody);

            JsonNode detail = error.path("Data").path("detail");

            String code = detail.path("code")
                    .asText("UNKNOWN");

            String message = detail.path("message")
                    .asText(error.path("message")
                            .asText("Gateway error"));

            return "code=" + code
                    + ", \"message\": "
                    + mapper.writeValueAsString(message);

        } catch (Exception e) {
            return "Unable to parse gateway error";
        }
    }

}
