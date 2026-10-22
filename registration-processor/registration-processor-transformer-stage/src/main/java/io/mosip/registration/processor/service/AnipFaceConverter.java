/*
package io.mosip.registration.processor.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AnipFaceConverter {

    private static final int WIDTH = 236;
    private static final int HEIGHT = 296;

    public String convertIsoFaceToBase64(byte[] faceBdb)
            throws Exception {

        if (faceBdb == null || faceBdb.length == 0) {
            throw new IllegalArgumentException("Face BDB is empty");
        }

        // Verify ISO face signature.
        if (faceBdb.length < 12
                || faceBdb[0] != 'F'
                || faceBdb[1] != 'A'
                || faceBdb[2] != 'C'
                || faceBdb[3] != 0) {
            throw new IllegalArgumentException(
                    "Invalid ISO face record");
        }

        // Locate JP2 signature.
        // This is a temporary implementation for the observed record.
        byte[] jp2Signature = {
                0, 0, 0, 12, 0x6A, 0x50, 0x20, 0x20,
                0x0D, 0x0A, (byte) 0x87, 0x0A
        };

        int offset = findSignature(faceBdb, jp2Signature);

        if (offset < 0) {
            throw new IllegalStateException(
                    "JPEG2000 JP2 image not found in ISO face record");
        }

        System.out.println("JP2 image offset = " + offset);

        // Extract candidate JP2 bytes.
        byte[] jp2Bytes =
                Arrays.copyOfRange(faceBdb, offset, faceBdb.length);

        // Decode JPEG2000 using installed ImageIO plugin.
        BufferedImage source = ImageIO.read(
                new ByteArrayInputStream(jp2Bytes));

        if (source == null) {
            throw new IllegalStateException(
                    "JPEG2000 image decoding failed");
        }

        System.out.println(
                "Original face dimensions = "
                        + source.getWidth()
                        + " x "
                        + source.getHeight());

        // Create RGB image with ANIP-required dimensions.
        BufferedImage output =
                new BufferedImage(
                        WIDTH,
                        HEIGHT,
                        BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = output.createGraphics();

        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            // Preserve aspect ratio; use white padding if needed.
            double scale = Math.min(
                    (double) WIDTH / source.getWidth(),
                    (double) HEIGHT / source.getHeight());

            int scaledWidth =
                    (int) Math.round(source.getWidth() * scale);

            int scaledHeight =
                    (int) Math.round(source.getHeight() * scale);

            int x = (WIDTH - scaledWidth) / 2;
            int y = (HEIGHT - scaledHeight) / 2;

            graphics.drawImage(
                    source,
                    x, y,
                    scaledWidth, scaledHeight,
                    null);

        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        if (!ImageIO.write(output, "png", outputStream)) {
            throw new IllegalStateException(
                    "PNG encoder not available");
        }

        byte[] pngBytes = outputStream.toByteArray();

        System.out.println(
                "ANIP PNG dimensions = 236 x 296");

        System.out.println(
                "ANIP PNG size = " + pngBytes.length);

        return Base64.getEncoder()
                .encodeToString(pngBytes);
    }

    private int findSignature(byte[] data, byte[] signature) {

        for (int i = 0;
             i <= data.length - signature.length;
             i++) {

            boolean matched = true;

            for (int j = 0; j < signature.length; j++) {
                if (data[i + j] != signature[j]) {
                    matched = false;
                    break;
                }
            }

            if (matched) {
                return i;
            }
        }

        return -1;
    }
}
*/

/*
package io.mosip.registration.processor.service;

import io.mosip.biometrics.util.CommonUtil;
import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.face.FaceBDIR;
import io.mosip.biometrics.util.face.FaceDecoder;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.Base64;

@Component
public class AnipFaceConverter {

    private static final int TARGET_WIDTH = 236;
    private static final int TARGET_HEIGHT = 296;

    public String convertIsoFaceToBase64(byte[] faceBdb)
            throws Exception {

        if (faceBdb == null || faceBdb.length == 0) {
            throw new IllegalArgumentException("Face BDB is empty");
        }

        // STEP 1: Prepare ISO face decoding request
        ConvertRequestDto dto = new ConvertRequestDto();

        dto.setModality("Face");
        dto.setVersion("ISO19794_5_2011");
        dto.setInputBytes(faceBdb);

        // STEP 2: Decode ISO face using biometrics-util
        FaceBDIR faceBDIR = FaceDecoder.getFaceBDIR(dto);

        // STEP 3: Extract image bytes using biometrics-util
        byte[] jp2Bytes = faceBDIR.getRepresentation()
                .getRepresentationData()
                .getImageData()
                .getImage();

        if (jp2Bytes == null || jp2Bytes.length == 0) {
            throw new IllegalStateException(
                    "Face image extraction failed");
        }

        // STEP 4: Convert JP2 to RGB PNG with target dimensions
        byte[] pngBytes = CommonUtil.convertJP2ToPNGBytes(
                jp2Bytes,
                TARGET_WIDTH,
                TARGET_HEIGHT,
                BufferedImage.TYPE_INT_RGB
        );

        if (pngBytes == null || pngBytes.length == 0) {
            throw new IllegalStateException(
                    "Face JP2 to PNG conversion failed");
        }

        // STEP 5: Log result
        System.out.println(
                "ANIP face converted successfully: "
                        + "target=" + TARGET_WIDTH + "x" + TARGET_HEIGHT
                        + ", PNG bytes=" + pngBytes.length
        );

        // STEP 6: Return Base64 PNG
        return Base64.getEncoder().encodeToString(pngBytes);
    }
}

*/
package io.mosip.registration.processor.service;

import io.mosip.biometrics.util.CommonUtil;
import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.face.FaceBDIR;
import io.mosip.biometrics.util.face.FaceDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Component
public class AnipFaceConverter {

    private static final Logger log =
            LoggerFactory.getLogger(AnipFaceConverter.class);

    private static final int TARGET_WIDTH = 236;
    private static final int TARGET_HEIGHT = 296;

    // PNG compression level: 0–9. This does not reduce image quality.
    private static final int PNG_COMPRESSION_LEVEL = 3;

    public String convertIsoFaceToBase64(byte[] faceBdb)
            throws Exception {

        if (faceBdb == null || faceBdb.length == 0) {
            throw new IllegalArgumentException("Face BDB is empty");
        }

        try {
            // STEP 1: Prepare ISO face decoding request.
            ConvertRequestDto dto = new ConvertRequestDto();
            dto.setModality("Face");
            dto.setVersion("ISO19794_5_2011");
            dto.setInputBytes(faceBdb);

            // STEP 2: Decode ISO face using biometrics-util.
            FaceBDIR faceBDIR = FaceDecoder.getFaceBDIR(dto);

            if (faceBDIR == null
                    || faceBDIR.getRepresentation() == null
                    || faceBDIR.getRepresentation()
                    .getRepresentationData() == null
                    || faceBDIR.getRepresentation()
                    .getRepresentationData()
                    .getImageData() == null) {

                throw new IllegalStateException(
                        "Decoded ISO face record contains no image data");
            }

            // STEP 3: Extract embedded JPEG2000 image.
            byte[] imageBytes = faceBDIR.getRepresentation()
                    .getRepresentationData()
                    .getImageData()
                    .getImage();

            if (imageBytes == null || imageBytes.length == 0) {
                throw new IllegalStateException(
                        "Face image extraction failed");
            }

            log.info(
                    "Face image extracted: bdbBytes={}, imageBytes={}",
                    faceBdb.length,
                    imageBytes.length
            );

            // STEP 4: Decode JPEG2000 through existing OpenCV utility.
            byte[] intermediatePng =
                    CommonUtil.convertJP2ToPNGUsingOpenCV(
                            imageBytes,
                            PNG_COMPRESSION_LEVEL
                    );

            if (intermediatePng == null
                    || intermediatePng.length == 0) {

                throw new IllegalStateException(
                        "OpenCV face conversion returned empty PNG");
            }

            // STEP 5: Read the PNG, which does not need a JP2 reader.
            BufferedImage source;

            try (ByteArrayInputStream input =
                         new ByteArrayInputStream(intermediatePng)) {

                source = ImageIO.read(input);
            }

            if (source == null) {
                throw new IOException(
                        "Unable to decode PNG generated by OpenCV");
            }

            log.info(
                    "Face decoded using OpenCV: original={}x{}",
                    source.getWidth(),
                    source.getHeight()
            );

            // STEP 6: Resize using the existing bioutils method.
            BufferedImage resized = CommonUtil.resizeImage(
                    source,
                    TARGET_WIDTH,
                    TARGET_HEIGHT,
                    BufferedImage.TYPE_INT_RGB
            );

            // STEP 7: Encode the final RGB image as PNG.
            byte[] pngBytes =
                    CommonUtil.convertBufferedImageToPNGBytes(resized);

            if (pngBytes == null || pngBytes.length == 0) {
                throw new IllegalStateException(
                        "Final face PNG is empty");
            }

            log.info(
                    "ANIP face converted successfully: "
                            + "dimensions={}x{}, pngBytes={}",
                    resized.getWidth(),
                    resized.getHeight(),
                    pngBytes.length
            );

            // STEP 8: Return Base64-encoded PNG.
            return Base64.getEncoder().encodeToString(pngBytes);

        } catch (Exception e) {
            log.error(
                    "ANIP face conversion failed: bdbBytes={}",
                    faceBdb.length,
                    e
            );

            throw e;
        }
    }
}
