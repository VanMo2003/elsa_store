
package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreMemberUpdateRequest {
    @Size(max = 100)
    String fullName;

    @Email
    @Size(max = 100)
    String email;

    @Size(max = 20)
    String phone;

    /** Khoá / mở tài khoản. */
    Boolean enabled;
}
