package com.example.elsa_store.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/** Trang chuyển liên kết http sang deep link: đích cố định và đích có id (chia sẻ sản phẩm / cửa hàng). */
class AppLinkControllerTest {

    private final AppLinkController controller = new AppLinkController();

    @Test
    void sharedProductAndStoreLinksOpenTheApp() {
        ResponseEntity<String> product = controller.openItem("products", 12L);
        assertEquals(HttpStatus.OK, product.getStatusCode());
        assertTrue(product.getBody().contains("elsastore://products/12"));
        assertTrue(product.getBody().contains("intent://products/12#Intent;scheme=elsastore;end"));
        assertTrue(controller.openItem("stores", 3L).getBody().contains("elsastore://stores/3"));
    }

    @Test
    void unknownTargetsAreNotServed() {
        assertEquals(HttpStatus.NOT_FOUND, controller.openItem("orders", 1L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.open("admin").getStatusCode());
        assertEquals(HttpStatus.OK, controller.open("orders").getStatusCode());
    }
}
