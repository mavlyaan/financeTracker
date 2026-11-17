package org.example.auth;

import jakarta.validation.constraints.*;

public class RegisterRequest {
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = "must contain 3-50 letters, digits or underscores")
    private String username;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    public RegisterRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
