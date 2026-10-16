package com.example.elsa_store.dto.request;

import com.example.elsa_store.constant.BannerLinkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 200)
    private String subtitle;

    @NotNull
    private BannerLinkType linkType;

    @Size(max = 200)
    private String linkValue;

    private Integer sortOrder;

    private Boolean active;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
}
