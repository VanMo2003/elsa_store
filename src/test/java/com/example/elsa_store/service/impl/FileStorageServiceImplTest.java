package com.example.elsa_store.service.impl;

import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceImplTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    @TempDir
    Path root;

    private FileStorageServiceImpl service() {
        // Đường dẫn tuyệt đối để test không ghi vào thư mục làm việc của dự án.
        return new FileStorageServiceImpl(root.toString().replace('\\', '/') + "/uploads/");
    }

    @Test
    void storesUnderARandomNameAndIgnoresTheClientFileName() throws Exception {
        String url = service().storeProductImage(
                new MockMultipartFile("files", "../../../../evil.jsp", "image/png", PNG));

        assertTrue(url.endsWith(".png"), url);
        assertTrue(!url.contains("evil") && !url.contains(".."), url);
        Path productDir = root.resolve("uploads/products");
        try (Stream<Path> files = Files.list(productDir)) {
            Path stored = files.findFirst().orElseThrow();
            assertArrayEquals(PNG, Files.readAllBytes(stored));
            // Đường dẫn công khai mà app ghép với địa chỉ BE: .../uploads/products/<tên đã lưu>.
            assertTrue(url.endsWith("/uploads/products/" + stored.getFileName()), url);
        }
        // Không có file nào bị ghi ra ngoài thư mục ảnh.
        try (Stream<Path> all = Files.walk(root)) {
            assertEquals(1, all.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void storeLogosGoToTheirOwnFolderWithTheSameChecks() throws Exception {
        String url = service().storeStoreImage(new MockMultipartFile("file", "logo.png", "image/png", PNG));
        try (Stream<Path> files = Files.list(root.resolve("uploads/stores"))) {
            assertTrue(url.endsWith("/uploads/stores/" + files.findFirst().orElseThrow().getFileName()), url);
        }
        MockMultipartFile html = new MockMultipartFile("file", "logo.png", "image/png", "<svg/>".getBytes(StandardCharsets.UTF_8));
        assertThrows(AppException.class, () -> service().storeStoreImage(html));
    }

    @Test
    void rejectsAnHtmlFilePretendingToBeAnImage() {
        MockMultipartFile html = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        AppException ex = assertThrows(AppException.class, () -> service().storeProductImage(html));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals(FileStorageServiceImpl.UNSUPPORTED_IMAGE, ex.getMessage());
    }

    @Test
    void rejectsAnEmptyFile() {
        AppException ex = assertThrows(
                AppException.class,
                () -> service().storeProductImage(new MockMultipartFile("files", "a.png", "image/png", new byte[0])));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void recognisesTheSupportedFormatsByTheirSignature() {
        assertEquals("jpg", FileStorageServiceImpl.imageExtension(JPEG));
        assertEquals("png", FileStorageServiceImpl.imageExtension(PNG));
        assertEquals("gif", FileStorageServiceImpl.imageExtension("GIF89a....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("webp", FileStorageServiceImpl.imageExtension("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
        assertNull(FileStorageServiceImpl.imageExtension("<svg onload=alert(1)>".getBytes(StandardCharsets.US_ASCII)));
        assertNull(FileStorageServiceImpl.imageExtension(new byte[] {(byte) 0xFF}));
        assertNull(FileStorageServiceImpl.imageExtension(null));
    }
}
