package com.ecommerce.pattern.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String recipientEmail;
    private Long recipientId;
    private String title;
    private String content;

}