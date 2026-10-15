package com.example.elsa_store.service.impl;

import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

/**
 * Lưu ảnh tải lên vào {@code file.upload-dir}. Tên file gốc từ máy khách không bao giờ được dùng làm đường dẫn (tránh
 * {@code ../} ghi ra ngoài thư mục), và loại ảnh được nhận diện từ chữ ký đầu file thay vì tin đuôi file hay
 * Content-Type — nên không thể đưa một trang HTML/SVG có mã độc vào {@code /uploads} (thư mục được phục vụ công khai).
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    static final String UNSUPPORTED_IMAGE = "Only JPEG, PNG, WEBP or GIF images can be uploaded";

    private static final String PRODUCT_FOLDER = "products";
    private static final String STORE_FOLDER = "stores";
    private static final String REVIEW_FOLDER = "reviews";

    private final String uploadDir;

    public FileStorageServiceImpl(@Value("${file.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
    }

    @Override
    public String storeProductImage(MultipartFile file) {
        return storeImage(file, PRODUCT_FOLDER);
    }

    @Override
    public String storeStoreImage(MultipartFile file) {
        return storeImage(file, STORE_FOLDER);
    }

    @Override
    public String storeReviewImage(MultipartFile file) {
        return storeImage(file, REVIEW_FOLDER);
    }

    private String storeImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "files: must not be empty");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("Cannot read uploaded file", e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Upload failed");
        }
        String extension = imageExtension(bytes);
        if (extension == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, UNSUPPORTED_IMAGE);
        }
        String name = UUID.randomUUID() + "." + extension;
        Path dir = Path.of(uploadDir, folder).toAbsolutePath().normalize();
        Path target = dir.resolve(name).normalize();
        try {
            Files.createDirectories(dir);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.error("Cannot store uploaded image at {}", target, e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Upload failed");
        }
        return "/" + uploadDir + folder + "/" + name;
    }

    /** Đuôi file theo chữ ký đầu file, hoặc {@code null} nếu không phải ảnh được hỗ trợ. */
    static String imageExtension(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (startsWithAscii(bytes, 0, "GIF87a") || startsWithAscii(bytes, 0, "GIF89a")) {
            return "gif";
        }
        if (startsWithAscii(bytes, 0, "RIFF") && startsWithAscii(bytes, 8, "WEBP")) {
            return "webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes == null || bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] bytes, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        return bytes != null
                && bytes.length >= offset + expected.length
                && Arrays.equals(bytes, offset, offset + expected.length, expected, 0, expected.length);
    }
}
