package com.example.elsa_store.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatMessageImagesTest {

    @Test
    void imagesRoundTripThroughOneColumn() {
        List<String> urls = List.of("/uploads/chat/a.png", "/uploads/chat/b.jpg");
        ChatMessage message = ChatMessage.builder().imageUrls(ChatMessage.joinImages(urls)).build();
        assertEquals(urls, message.imageList());
        assertNull(ChatMessage.joinImages(List.of()));
        assertEquals(List.of(), ChatMessage.builder().build().imageList());
    }
}
