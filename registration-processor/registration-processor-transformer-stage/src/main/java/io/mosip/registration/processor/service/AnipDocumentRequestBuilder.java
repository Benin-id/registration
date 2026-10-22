package io.mosip.registration.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.mosip.registration.processor.packet.storage.dto.Document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

@Component
public class AnipDocumentRequestBuilder {

    private static final Logger log =
            LoggerFactory.getLogger(AnipDocumentRequestBuilder.class);

    @Autowired
    private ObjectMapper mapper;

    public ArrayNode buildImagesContexte(
            Map<String, Document> documents) throws Exception {

        ArrayNode images = mapper.createArrayNode();

        if (documents == null || documents.isEmpty()) {
            log.warn("No documents available for ANIP context images");
            return images;
        }

        for (Map.Entry<String, Document> entry : documents.entrySet()) {

            if (images.size() >= 6) {
                break;
            }

            Document document = entry.getValue();

            if (document == null
                    || document.getDocument() == null
                    || document.getDocument().length == 0) {

                log.warn("Empty document; mappingKey={}", entry.getKey());
                continue;
            }

            byte[] documentBytes = document.getDocument();

            // Validate JPEG format without re-encoding the image.
            validateJpeg(documentBytes, entry.getKey());

            images.addObject().put(
                    "data_base64",
                    Base64.getEncoder().encodeToString(documentBytes)
            );

            log.info(
                    "ANIP context image prepared; "
                            + "mappingKey={}, format=JPEG, bytes={}",
                    entry.getKey(),
                    documentBytes.length
            );
        }

        log.info("Total ANIP context images prepared={}", images.size());

        return images;
    }

    private void validateJpeg(
            byte[] bytes,
            String mappingKey) throws Exception {

        try (ImageInputStream input =
                     ImageIO.createImageInputStream(
                             new ByteArrayInputStream(bytes))) {

            if (input == null) {
                throw new IllegalArgumentException(
                        "Unable to read document; mappingKey=" + mappingKey);
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "Unrecognized document format; mappingKey="
                                + mappingKey);
            }

            ImageReader reader = readers.next();

            try {
                String format = reader.getFormatName();

                if (!"JPEG".equalsIgnoreCase(format)
                        && !"JPG".equalsIgnoreCase(format)) {

                    throw new IllegalArgumentException(
                            "Expected JPEG document but received "
                                    + format
                                    + "; mappingKey=" + mappingKey);
                }

                reader.setInput(input);
                reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}
