/*package io.mosip.registration.processor.service;

import io.mosip.biometrics.util.CommonUtil;
import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.finger.FingerDecoder;
import org.jnbis.Bitmap;
import org.jnbis.WSQEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AnipFingerprintConverter {

    private static final Logger log =
            LoggerFactory.getLogger(AnipFingerprintConverter.class);

    private static final int TARGET_WIDTH = 256;
    private static final int TARGET_HEIGHT = 400;
    private static final int TARGET_PPI = 500;

    private static final byte[] JP2_SIGNATURE = {
            0x00, 0x00, 0x00, 0x0C,
            0x6A, 0x50, 0x20, 0x20,
            0x0D, 0x0A, (byte) 0x87, 0x0A
    };

     public String convertToWsqBase64(byte[] fingerBdb)
             throws IOException {

         if (fingerBdb == null || fingerBdb.length == 0) {
             throw new IllegalArgumentException(
                     "Fingerprint BDB is empty");
         }

         // STEP 1: Locate JPEG2000 image in MOSIP BDB
         int offset = findJp2Offset(fingerBdb);

         if (offset < 0) {
             throw new IOException(
                     "JPEG2000 image not found in fingerprint BDB");
         }

         log.info("Fingerprint JP2 offset = {}", offset);

         // STEP 2: Extract JPEG2000 bytes
         byte[] jp2Bytes = Arrays.copyOfRange(
                 fingerBdb,
                 offset,
                 fingerBdb.length
         );

         // STEP 3: Decode JPEG2000 image
         BufferedImage image = ImageIO.read(
                 new ByteArrayInputStream(jp2Bytes)
         );

         if (image == null) {
             throw new IOException(
                     "Unable to decode JPEG2000 fingerprint");
         }

         int originalWidth = image.getWidth();
         int originalHeight = image.getHeight();

         log.info(
                 "Original fingerprint dimensions = {}x{}",
                 originalWidth,
                 originalHeight
         );

         // STEP 4: Resize to ANIP-required dimensions
         // TEST ONLY: Direct resizing may distort ridges.
         if (originalWidth != TARGET_WIDTH
                 || originalHeight != TARGET_HEIGHT) {

             BufferedImage resizedImage =
                     new BufferedImage(
                             TARGET_WIDTH,
                             TARGET_HEIGHT,
                             BufferedImage.TYPE_BYTE_GRAY
                     );

             Graphics2D graphics =
                     resizedImage.createGraphics();

             try {
                 graphics.setRenderingHint(
                         RenderingHints.KEY_INTERPOLATION,
                         RenderingHints.VALUE_INTERPOLATION_BICUBIC
                 );

                 graphics.setRenderingHint(
                         RenderingHints.KEY_RENDERING,
                         RenderingHints.VALUE_RENDER_QUALITY
                 );

                 graphics.drawImage(
                         image,
                         0,
                         0,
                         TARGET_WIDTH,
                         TARGET_HEIGHT,
                         null
                 );

             } finally {
                 graphics.dispose();
             }

             image = resizedImage;

             log.warn(
                     "TEST ONLY: Fingerprint resized from {}x{} to {}x{}",
                     originalWidth,
                     originalHeight,
                     TARGET_WIDTH,
                     TARGET_HEIGHT
             );
         }

         int width = image.getWidth();
         int height = image.getHeight();

         if (width != TARGET_WIDTH
                 || height != TARGET_HEIGHT) {

             throw new IOException(
                     "Fingerprint dimensions invalid after conversion: "
                             + width + "x" + height
             );
         }

         // STEP 5: Extract 8-bit grayscale pixels
         byte[] pixels = new byte[width * height];

         for (int y = 0; y < height; y++) {
             for (int x = 0; x < width; x++) {

                 int rgb = image.getRGB(x, y);

                 int r = (rgb >> 16) & 0xFF;
                 int g = (rgb >> 8) & 0xFF;
                 int b = rgb & 0xFF;

                 int gray = (r + g + b) / 3;

                 pixels[y * width + x] = (byte) gray;
             }
         }

         // STEP 6: Create JNBIS Bitmap
         // 500 PPI must be verified from capture metadata.
         Bitmap bitmap = new Bitmap(
                 pixels,
                 width,
                 height,
                 TARGET_PPI,
                 8,
                 1
         );

         // STEP 7: Encode fingerprint as WSQ
         ByteArrayOutputStream output =
                 new ByteArrayOutputStream();

         WSQEncoder.encode(
                 output,
                 bitmap,
                 0.75,
                 new String[0]
         );

         byte[] wsqBytes = output.toByteArray();

         // STEP 8: Verify WSQ signature
         if (wsqBytes.length < 2
                 || (wsqBytes[0] & 0xFF) != 0xFF
                 || (wsqBytes[1] & 0xFF) != 0xA0) {

             throw new IOException(
                     "Generated fingerprint is not WSQ");
         }

         log.info(
                 "ANIP WSQ generated: dimensions={}x{}, "
                         + "declaredPpi={}, bytes={}",
                 width,
                 height,
                 TARGET_PPI,
                 wsqBytes.length
         );

         // STEP 9: Convert WSQ to Base64
         return Base64.getEncoder()
                 .encodeToString(wsqBytes);
     }

     private int findJp2Offset(byte[] data) {

         for (int i = 0;
              i <= data.length - JP2_SIGNATURE.length;
              i++) {

             boolean match = true;

             for (int j = 0;
                  j < JP2_SIGNATURE.length;
                  j++) {

                 if (data[i + j] != JP2_SIGNATURE[j]) {
                     match = false;
                     break;
                 }
             }

             if (match) {
                 return i;
             }
         }

         return -1;
     }*/
package io.mosip.registration.processor.service;

import io.mosip.biometrics.util.CommonUtil;
import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.finger.FingerBDIR;
import io.mosip.biometrics.util.finger.FingerDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Component
public class AnipFingerprintConverter {

    private static final Logger log =
            LoggerFactory.getLogger(AnipFingerprintConverter.class);

    private static final int TARGET_WIDTH = 256;
    private static final int TARGET_HEIGHT = 400;

    // Existing CommonUtil.convertBufferedImageToWSQ() declares 500 PPI.
    private static final int DECLARED_PPI = 500;

    private static final float WSQ_BITRATE = 0.75f;
    private static final int PNG_COMPRESSION_LEVEL = 3;

    public String convertToWsqBase64(byte[] fingerBdb)
            throws Exception {

        return convertToWsq(fingerBdb).getBase64();
    }

    public WsqResult convertToWsq(byte[] fingerBdb)
            throws Exception {

        if (fingerBdb == null || fingerBdb.length == 0) {
            throw new IllegalArgumentException(
                    "Fingerprint BDB is empty");
        }

        try {
            // STEP 1: Prepare ISO fingerprint decoding request.
            ConvertRequestDto requestDto = new ConvertRequestDto();
            requestDto.setModality("Finger");
            requestDto.setVersion("ISO19794_4_2011");
            requestDto.setInputBytes(fingerBdb);

            log.info(
                    "Fingerprint conversion started: bdbBytes={}",
                    fingerBdb.length
            );

            // STEP 2: Extract the embedded JPEG2000 image.
            FingerBDIR fingerBDIR =
                    FingerDecoder.getFingerBDIR(requestDto);

            if (fingerBDIR == null) {
                throw new IllegalStateException(
                        "ISO fingerprint decoding returned no record");
            }

            byte[] jp2Bytes = fingerBDIR.getImage();

            if (jp2Bytes == null || jp2Bytes.length == 0) {
                throw new IllegalStateException(
                        "Fingerprint JP2 extraction failed");
            }

            log.info(
                    "Fingerprint JP2 extracted: bytes={}",
                    jp2Bytes.length
            );

            // STEP 3: Decode JP2 using existing OpenCV utility.
            // PNG is an internal lossless intermediate only.
            byte[] decodedPng =
                    CommonUtil.convertJP2ToPNGUsingOpenCV(
                            jp2Bytes,
                            PNG_COMPRESSION_LEVEL
                    );

            if (decodedPng == null || decodedPng.length == 0) {
                throw new IllegalStateException(
                        "OpenCV fingerprint decoding returned empty PNG");
            }

            // STEP 4: Read PNG without needing a JPEG2000 ImageIO reader.
            BufferedImage source;

            try (ByteArrayInputStream input =
                         new ByteArrayInputStream(decodedPng)) {

                source = ImageIO.read(input);
            }

            if (source == null) {
                throw new IOException(
                        "Unable to read fingerprint PNG generated by OpenCV");
            }

            int originalWidth = source.getWidth();
            int originalHeight = source.getHeight();

            // STEP 5: Temporary test resizing to 8-bit grayscale.
            BufferedImage resized = CommonUtil.resizeImage(
                    source,
                    TARGET_WIDTH,
                    TARGET_HEIGHT,
                    BufferedImage.TYPE_BYTE_GRAY
            );

            // STEP 6: Encode WSQ once using existing bioutils.
            byte[] wsqBytes =
                    CommonUtil.convertBufferedImageToWSQ(
                            resized,
                            WSQ_BITRATE
                    );

            if (wsqBytes == null || wsqBytes.length < 2) {
                throw new IllegalStateException(
                        "WSQ conversion returned empty output");
            }

            // STEP 7: Check WSQ start marker.
            if ((wsqBytes[0] & 0xFF) != 0xFF
                    || (wsqBytes[1] & 0xFF) != 0xA0) {

                throw new IllegalStateException(
                        "Generated fingerprint has an invalid WSQ start marker");
            }

            log.warn(
                    "TEST ONLY: Fingerprint resized: "
                            + "original={}x{}, target={}x{}, "
                            + "declaredPpi={}, bitrate={}, wsqBytes={}",
                    originalWidth,
                    originalHeight,
                    TARGET_WIDTH,
                    TARGET_HEIGHT,
                    DECLARED_PPI,
                    WSQ_BITRATE,
                    wsqBytes.length
            );

            // STEP 8: Return Base64 WSQ and output dimensions.
            return new WsqResult(
                    Base64.getEncoder().encodeToString(wsqBytes),
                    TARGET_WIDTH,
                    TARGET_HEIGHT
            );

        } catch (Exception e) {
            log.error(
                    "Fingerprint conversion failed: bdbBytes={}",
                    fingerBdb.length,
                    e
            );

            throw e;
        }
    }

    public static final class WsqResult {

        private final String base64;
        private final int width;
        private final int height;

        public WsqResult(
                String base64,
                int width,
                int height) {

            this.base64 = base64;
            this.width = width;
            this.height = height;
        }

        public String getBase64() {
            return base64;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
